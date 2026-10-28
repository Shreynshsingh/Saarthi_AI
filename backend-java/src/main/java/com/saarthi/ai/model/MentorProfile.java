package com.saarthi.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "mentor_name", length = 100)
    private String mentorName;

    @Column(name = "avatar_image_url", columnDefinition = "TEXT")
    private String avatarImageUrl;

    @Column(name = "avatar_provider_id", columnDefinition = "TEXT")
    private String avatarProviderId;

    @Column(name = "voice_id", length = 100)
    private String voiceId;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (mentorName == null) {
            mentorName = "Saarthi";
        }
        if (status == null) {
            status = "STATIC_READY";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
