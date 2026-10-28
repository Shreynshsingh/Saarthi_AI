package com.saarthi.ai.repository;

import com.saarthi.ai.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
