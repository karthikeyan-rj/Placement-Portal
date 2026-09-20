package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoMessage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MongoMessageRepository extends MongoRepository<MongoMessage, String> {

    Optional<MongoMessage> findByMessageId(Long messageId);

    List<MongoMessage> findBySenderUserIdOrderByCreatedAtDesc(Long senderUserId, Pageable pageable);

    List<MongoMessage> findByMessageIdIn(Collection<Long> messageIds);

    long countBySenderUserId(Long senderUserId);
}