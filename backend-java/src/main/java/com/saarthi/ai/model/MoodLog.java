package com.saarthi.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mood_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "mood", nullable = false, columnDefinition = "mood_type")
    private MoodType mood;

    @Column
    private Float confidence;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "logged_at", updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        if (loggedAt == null) {
            loggedAt = LocalDateTime.now();
        }
    }
}
