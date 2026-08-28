package com.college.placement.messaging;

import com.college.placement.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageId(Long messageId);

    Optional<MessageRecipient> findByMessageIdAndRecipientId(Long messageId, Long recipientId);

    List<MessageRecipient> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    boolean existsByMessageIdAndRecipientId(Long messageId, Long recipientId);

    long countByMessageId(Long messageId);

    long countByMessageIdAndDeliveredAtIsNotNull(Long messageId);

    long countByMessageIdAndReadAtIsNotNull(Long messageId);
}
