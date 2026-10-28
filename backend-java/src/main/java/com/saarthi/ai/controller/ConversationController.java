package com.saarthi.ai.controller;

import com.saarthi.ai.agent.core.MemoryAgent;
import com.saarthi.ai.dto.ApiResponse;
import com.saarthi.ai.dto.ConversationSummaryResponse;
import com.saarthi.ai.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboard "recent conversations" list. Identity always comes from the JWT
 * principal. Summaries only - full transcripts stay in the live session.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final MemoryAgent memoryAgent;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> recent(
            Authentication authentication,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        UUID userId = currentUserId(authentication);
        List<ConversationSummaryResponse> rows = memoryAgent.getRecentConversations(userId, limit)
                .stream()
                .map(row -> new ConversationSummaryResponse(row.id(), row.title(),
                        row.startedAt() == null ? null : row.startedAt().toString(),
                        row.preview()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Recent conversations", rows));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new NotFoundException("Please log in again to continue.");
        }
        return userId;
    }
}
