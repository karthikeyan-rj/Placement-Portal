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
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageReaction> findByMessageId(Long messageId);

    long countByMessageIdAndReaction(Long messageId, com.college.placement.common.enums.MessageReactionType reaction);

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    @Query("SELECT mr.message.id AS messageId, " +
            "SUM(CASE WHEN mr.reaction = com.college.placement.common.enums.MessageReactionType.UPVOTE THEN 1 ELSE 0 END) AS upvotes, " +
            "SUM(CASE WHEN mr.reaction = com.college.placement.common.enums.MessageReactionType.DOWNVOTE THEN 1 ELSE 0 END) AS downvotes " +
            "FROM MessageReaction mr WHERE mr.message.id IN :ids GROUP BY mr.message.id")
    List<MessageReactionStats> aggregateStats(@Param("ids") Collection<Long> ids);

    interface MessageReactionStats {
        Long getMessageId();
        Long getUpvotes();
        Long getDownvotes();
    }
}
