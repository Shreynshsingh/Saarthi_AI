package com.saarthi.ai.repository;

import com.saarthi.ai.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByStartedAtDesc(UUID userId);
}
