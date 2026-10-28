package com.saarthi.ai.repository;

import com.saarthi.ai.model.MoodLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MoodLogRepository extends JpaRepository<MoodLog, UUID> {

    List<MoodLog> findByUserIdOrderByLoggedAtDesc(UUID userId, Pageable pageable);

    List<MoodLog> findByUserIdAndLoggedAtAfterOrderByLoggedAtDesc(UUID userId, LocalDateTime after);
}
