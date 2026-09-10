package com.college.placement.messaging;

import com.college.placement.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageId(Long messageId);

    @Query("SELECT mr FROM MessageRecipient mr JOIN FETCH mr.recipient r LEFT JOIN FETCH r.department WHERE mr.message.id = :messageId")
    List<MessageRecipient> findByMessageIdWithUserAndDepartment(@Param("messageId") Long messageId);

    Optional<MessageRecipient> findByMessageIdAndRecipientId(Long messageId, Long recipientId);

    List<MessageRecipient> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    boolean existsByMessageIdAndRecipientId(Long messageId, Long recipientId);

    long countByMessageId(Long messageId);

    long countByMessageIdAndDeliveredAtIsNotNull(Long messageId);

    long countByMessageIdAndReadAtIsNotNull(Long messageId);

    @Query("SELECT mr.message.id AS messageId, COUNT(mr) AS total, " +
            "COUNT(mr.deliveredAt) AS delivered, COUNT(mr.readAt) AS read " +
            "FROM MessageRecipient mr WHERE mr.message.id IN :ids GROUP BY mr.message.id")
    List<MessageRecipientStats> aggregateStats(@Param("ids") Collection<Long> ids);

    @Query(value = "SELECT mr.message_id AS messageId, " +
            "COUNT(DISTINCT mr.id) AS total, " +
            "COUNT(DISTINCT mr.id) FILTER (WHERE mr.delivered_at IS NOT NULL) AS delivered, " +
            "COUNT(DISTINCT mr.id) FILTER (WHERE mr.read_at IS NOT NULL) AS readCount, " +
            "COUNT(DISTINCT r.id) FILTER (WHERE r.reaction = 'UPVOTE') AS upvotes, " +
            "COUNT(DISTINCT r.id) FILTER (WHERE r.reaction = 'DOWNVOTE') AS downvotes " +
            "FROM message_recipients mr " +
            "LEFT JOIN message_reactions r ON r.message_id = mr.message_id " +
            "WHERE mr.message_id IN (:ids) GROUP BY mr.message_id",
            nativeQuery = true)
    List<CombinedMessageStats> aggregateAllStats(@Param("ids") Collection<Long> ids);

    interface MessageRecipientStats {
        Long getMessageId();
        Long getTotal();
        Long getDelivered();
        Long getRead();
    }

    interface CombinedMessageStats extends MessageRecipientStats {
        Long getReadCount();
        Long getUpvotes();
        Long getDownvotes();
    }
}
