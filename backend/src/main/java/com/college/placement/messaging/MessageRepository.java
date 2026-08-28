package com.college.placement.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT mr.message FROM MessageRecipient mr WHERE mr.recipient.id = :userId ORDER BY mr.message.createdAt DESC")
    Page<Message> findMessagesReceivedByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(mr) FROM MessageRecipient mr WHERE mr.message.id = :messageId")
    long countRecipients(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(mr) FROM MessageRecipient mr WHERE mr.message.id = :messageId AND mr.deliveredAt IS NOT NULL")
    long countDelivered(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(mr) FROM MessageRecipient mr WHERE mr.message.id = :messageId AND mr.readAt IS NOT NULL")
    long countRead(@Param("messageId") Long messageId);
}
