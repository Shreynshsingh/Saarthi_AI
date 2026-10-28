package com.saarthi.ai.controller;

import com.saarthi.ai.agent.core.MemoryAgent;
import com.saarthi.ai.dto.ApiResponse;
import com.saarthi.ai.dto.CheckinRequest;
import com.saarthi.ai.dto.MoodEntryResponse;
import com.saarthi.ai.dto.MoodInsightResponse;
import com.saarthi.ai.exception.NotFoundException;
import com.saarthi.ai.model.MoodLog;
import com.saarthi.ai.service.MoodInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticated mood endpoints for the dashboard. Identity always comes from
 * the JWT principal - never from query params or bodies.
 */
@RestController
@RequestMapping("/api/v1/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MemoryAgent memoryAgent;
    private final MoodInsightService insightService;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MoodEntryResponse>>> history(
            Authentication authentication,
            @RequestParam(value = "days", defaultValue = "7") int days) {
        UUID userId = currentUserId(authentication);
        List<MoodEntryResponse> entries = memoryAgent.getMoodHistory(userId, days).stream()
                .map(this::toEntry)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Mood history", entries));
    }

    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<MoodEntryResponse>> checkin(
            Authentication authentication,
            @Valid @RequestBody CheckinRequest request) {
        UUID userId = currentUserId(authentication);
        MoodLog saved = memoryAgent.saveCheckin(userId, request.mood(), request.note());
        return ResponseEntity.ok(ApiResponse.success("Checked in", toEntry(saved)));
    }

    @GetMapping("/insight")
    public ResponseEntity<ApiResponse<MoodInsightResponse>> insight(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Mood insight",
                new MoodInsightResponse(insightService.generateInsight(userId))));
    }

    private MoodEntryResponse toEntry(MoodLog log) {
        return new MoodEntryResponse(log.getId(), log.getMood().name(), log.getConfidence(),
                log.getNote(), log.getLoggedAt() == null ? null : log.getLoggedAt().toString(),
                log.getConversationId());
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new NotFoundException("Please log in again to continue.");
        }
        return userId;
    }
}
