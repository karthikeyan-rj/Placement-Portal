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

    @Query("SELECT mr.message.id AS messageId, (mr.readAt IS NOT NULL) AS readFlag " +
            "FROM MessageRecipient mr WHERE mr.message.id IN :ids AND mr.recipient.id = :recipientId")
    List<MessageReadFlag> findReadFlags(@Param("ids") Collection<Long> ids, @Param("recipientId") Long recipientId);

    interface MessageRecipientStats {
        Long getMessageId();
        Long getTotal();
        Long getDelivered();
        Long getRead();
    }

    interface MessageReadFlag {
        Long getMessageId();
        Boolean getReadFlag();
    }
}
