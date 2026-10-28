package com.saarthi.ai.repository;

import com.saarthi.ai.model.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {

    Optional<MentorProfile> findByUserId(UUID userId);
}
