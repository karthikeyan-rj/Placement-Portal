package com.college.placement.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClarificationThreadRepository extends JpaRepository<ClarificationThread, Long> {

    Optional<ClarificationThread> findByMessageIdAndRequesterId(Long messageId, Long requesterId);

    boolean existsByMessageIdAndRequesterId(Long messageId, Long requesterId);

    @EntityGraph(attributePaths = {"requester", "sender"})
    Page<ClarificationThread> findByMessageIdOrderByUpdatedAtDesc(Long messageId, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "message"})
    Page<ClarificationThread> findBySenderIdOrderByUpdatedAtDesc(Long senderId, Pageable pageable);

    long countByMessageId(Long messageId);

    long countByMessageIdAndStatus(Long messageId, ClarificationStatus status);

    @Query("SELECT t.message.id AS messageId, COUNT(t) AS total, " +
            "COUNT(CASE WHEN t.status = com.college.placement.messaging.ClarificationStatus.OPEN THEN 1 END) AS open " +
            "FROM ClarificationThread t WHERE t.message.id IN :ids GROUP BY t.message.id")
    List<ClarificationSummaryStats> aggregateSummary(@Param("ids") Collection<Long> ids);

    interface ClarificationSummaryStats {
        Long getMessageId();
        Long getTotal();
        Long getOpen();
    }
}