package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * Mentor profile payload: {mentorProfileId, mentorName, avatarImageUrl,
 * avatarProviderId, status}. Status is READY (live avatar bound) or
 * STATIC_READY (photo kept for static photo + voice mode).
 */
public record MentorProfileResponse(UUID mentorProfileId, String mentorName, String avatarImageUrl,
                                    String avatarProviderId, String status) {
}
