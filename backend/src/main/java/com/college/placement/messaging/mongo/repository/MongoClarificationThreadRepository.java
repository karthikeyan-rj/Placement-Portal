package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoClarificationThread;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MongoClarificationThreadRepository extends MongoRepository<MongoClarificationThread, String> {

    Optional<MongoClarificationThread> findByThreadId(Long threadId);

    Optional<MongoClarificationThread> findByMessageIdAndRequesterUserId(Long messageId, Long requesterUserId);

    List<MongoClarificationThread> findBySenderUserIdOrderByUpdatedAtDesc(Long senderUserId, Pageable pageable);

    List<MongoClarificationThread> findByRequesterUserIdOrderByUpdatedAtDesc(Long requesterUserId, Pageable pageable);

    List<MongoClarificationThread> findByMessageIdOrderByUpdatedAtDesc(Long messageId, Pageable pageable);

    long countByMessageId(Long messageId);

    long countByMessageIdAndStatus(Long messageId, String status);

    long countBySenderUserId(Long senderUserId);
}