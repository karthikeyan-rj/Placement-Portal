package com.college.placement.messaging;

import com.college.placement.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    long countByMessageIdAndReaction(Long messageId, com.college.placement.common.enums.MessageReactionType reaction);

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);
}
