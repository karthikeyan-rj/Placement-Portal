package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoMessageRecipient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MongoMessageRecipientRepository extends MongoRepository<MongoMessageRecipient, String> {

    List<MongoMessageRecipient> findByMessageId(Long messageId);

    List<MongoMessageRecipient> findByMessageIdOrderByRecipientUserIdAsc(Long messageId);

    Optional<MongoMessageRecipient> findByMessageIdAndRecipientUserId(Long messageId, Long recipientUserId);

    List<MongoMessageRecipient> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    boolean existsByMessageIdAndRecipientUserId(Long messageId, Long recipientUserId);
}