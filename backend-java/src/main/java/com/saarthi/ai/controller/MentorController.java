package com.saarthi.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.agent.action.avatar.AvatarAgent;
import com.saarthi.ai.agent.action.avatar.AvatarStreamRegistry;
import com.saarthi.ai.agent.action.avatar.AvatarStreamSession;
import com.saarthi.ai.agent.action.avatar.AvatarUnavailableException;
import com.saarthi.ai.dto.ApiResponse;
import com.saarthi.ai.dto.MentorProfileResponse;
import com.saarthi.ai.exception.NotFoundException;
import com.saarthi.ai.service.MentorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticated mentor endpoints. Identity always comes from the JWT principal
 * (a UUID set by JwtAuthFilter) — never from form data, query params, or bodies.
 *
 * NOTE: D-ID cannot fetch localhost URLs. For local avatar generation tests,
 * if D-ID cannot reach the image the profile is kept with status
 * FALLBACK_STATIC (STATIC_READY) and the photo still serves static mode.
 * A deployed backend URL will work.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mentor")
@RequiredArgsConstructor
public class MentorController {

    public record StreamSetup(String provider, String streamId, String sessionId, String sdpOffer,
                              List<Map<String, Object>> iceServers, String fallbackReason) {
        static StreamSetup staticMode() {
            return new StreamSetup("static", null, null, null, null, null);
        }

        static StreamSetup fallback() {
            return new StreamSetup("static", null, null, null, null, "avatar_unavailable");
        }
    }

    public record SdpBody(String sessionId, String sdpAnswer) {
    }

    public record IceBody(String sessionId, String candidate, String sdpMid, Integer sdpMLineIndex) {
    }

    private final MentorService mentorService;
    private final AvatarAgent avatarAgent;
    private final AvatarStreamRegistry avatarStreams;
    private final ObjectMapper objectMapper;

    @PostMapping("/upload-avatar")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mentorName", required = false) String mentorName,
            @RequestParam(value = "voiceId", required = false) String voiceId) {
        UUID userId = currentUserId(authentication);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        MentorProfileResponse dto = mentorService.uploadAvatar(userId, file, mentorName, voiceId, baseUrl);
        return ResponseEntity.ok(ApiResponse.success("Your mentor is ready!", dto));
    }

    @GetMapping("/my-mentor")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> myMentor(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        MentorProfileResponse dto = mentorService.myMentor(userId).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(
                dto == null ? "No mentor yet" : "Mentor found", dto));
    }

    /**
     * Opens a live avatar stream for the caller's own mentor photo. Always
     * HTTP 200: provider "did" with SDP, or provider "static" (with a fallback
     * reason when D-ID was tried and failed). Never a 500 — the session must
     * always be able to start.
     */
    @PostMapping("/avatar-stream")
    public ResponseEntity<ApiResponse<StreamSetup>> createAvatarStream(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        String photoUrl = mentorService.myMentor(userId)
                .map(MentorProfileResponse::avatarImageUrl)
                .orElse(null);
        if (photoUrl == null || photoUrl.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success("Static avatar mode", StreamSetup.staticMode()));
        }
        try {
            AvatarStreamSession session = avatarAgent.createStream(photoUrl);
            avatarStreams.register(userId, session.streamId(), session.sessionId());
            return ResponseEntity.ok(ApiResponse.success("Live avatar ready",
                    new StreamSetup("did", session.streamId(), session.sessionId(),
                            session.sdpOffer(), parseIceServers(session.iceServersJson()), null)));
        } catch (AvatarUnavailableException e) {
            return ResponseEntity.ok(ApiResponse.success("Static avatar mode", StreamSetup.fallback()));
        }
    }

    /** Thin forward of the browser's SDP answer. Ownership + session verified. */
    @PostMapping("/avatar-stream/{streamId}/sdp")
    public ResponseEntity<ApiResponse<Void>> sdpAnswer(Authentication authentication,
                                                       @PathVariable String streamId,
                                                       @RequestBody SdpBody body) {
        UUID userId = currentUserId(authentication);
        requireStreamId(streamId);
        if (body == null || body.sdpAnswer() == null || body.sdpAnswer().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing session answer."));
        }
        String sessionId = requireOwner(userId, streamId, body.sessionId());
        avatarAgent.submitSdpAnswer(streamId, sessionId, body.sdpAnswer());
        return ResponseEntity.ok(ApiResponse.success("Answer accepted", null));
    }

    /** Thin forward of one trickled ICE candidate. Ownership + session verified. */
    @PostMapping("/avatar-stream/{streamId}/ice")
    public ResponseEntity<ApiResponse<Void>> iceCandidate(Authentication authentication,
                                                          @PathVariable String streamId,
                                                          @RequestBody IceBody body) {
        UUID userId = currentUserId(authentication);
        requireStreamId(streamId);
        if (body != null && body.candidate() != null && !body.candidate().isBlank()) {
            String sessionId = requireOwner(userId, streamId, body.sessionId());
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("candidate", body.candidate());
            candidate.put("sdpMid", body.sdpMid());
            candidate.put("sdpMLineIndex", body.sdpMLineIndex());
            avatarAgent.submitIceCandidate(streamId, sessionId, candidate);
        }
        return ResponseEntity.ok(ApiResponse.success("Candidate accepted", null));
    }

    /** Best-effort teardown. sessionId via query (?sessionId=) or JSON body. */
    @DeleteMapping("/avatar-stream/{streamId}")
    public ResponseEntity<ApiResponse<Void>> closeAvatarStream(
            Authentication authentication,
            @PathVariable String streamId,
            @RequestParam(value = "sessionId", required = false) String sessionQuery,
            @RequestBody(required = false) Map<String, String> body) {
        UUID userId = currentUserId(authentication);
        requireStreamId(streamId);
        String sessionId = sessionQuery != null ? sessionQuery
                : body == null ? null : body.get("sessionId");
        String verified = requireOwner(userId, streamId, sessionId);
        avatarStreams.remove(streamId);
        avatarAgent.closeStream(streamId, verified);
        return ResponseEntity.ok(ApiResponse.success("Stream closed", null));
    }

    private void requireStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            throw new NotFoundException("Stream not found.");
        }
    }

    private String requireOwner(UUID userId, String streamId, String sessionId) {
        String expected = avatarStreams.verify(userId, streamId);
        if (expected == null || sessionId == null || !expected.equals(sessionId)) {
            throw new NotFoundException("Stream not found.");
        }
        return expected;
    }

    private List<Map<String, Object>> parseIceServers(String iceServersJson) {
        if (iceServersJson == null || iceServersJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(iceServersJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[Avatar] Unparseable ICE servers, continuing without them");
            return List.of();
        }
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new NotFoundException("Please log in again to continue.");
        }
        return userId;
    }
}
