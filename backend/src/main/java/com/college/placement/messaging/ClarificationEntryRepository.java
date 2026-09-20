package com.college.placement.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClarificationEntryRepository extends JpaRepository<ClarificationEntry, Long> {

    @EntityGraph(attributePaths = {"author"})
    Page<ClarificationEntry> findByThreadIdOrderByCreatedAtAsc(Long threadId, Pageable pageable);
}