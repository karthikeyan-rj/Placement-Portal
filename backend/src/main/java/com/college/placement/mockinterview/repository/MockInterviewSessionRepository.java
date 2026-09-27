package com.college.placement.mockinterview.repository;

import com.college.placement.common.enums.MockInterviewStatus;
import com.college.placement.mockinterview.domain.MockInterviewSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockInterviewSessionRepository extends JpaRepository<MockInterviewSession, Long> {

    /** The student's unfinished interview, if any. Backs resume. */
    Optional<MockInterviewSession> findFirstByStudentProfileIdAndStatusOrderByStartedAtDesc(
            Long studentProfileId, MockInterviewStatus status);

    /**
     * Ownership-scoped fetch. A session belonging to another student simply does
     * not match, so cross-student access is reported as "not found" and never
     * confirms that someone else's session exists.
     */
    @Query("SELECT s FROM MockInterviewSession s WHERE s.id = :id AND s.studentProfile.id = :studentProfileId")
    Optional<MockInterviewSession> findOwnedSession(
            @Param("id") Long id, @Param("studentProfileId") Long studentProfileId);

    Page<MockInterviewSession> findByStudentProfileIdOrderByCreatedAtDesc(
            Long studentProfileId, Pageable pageable);

    Page<MockInterviewSession> findByStudentProfileIdAndStatusOrderByCreatedAtDesc(
            Long studentProfileId, MockInterviewStatus status, Pageable pageable);

    List<MockInterviewSession> findByStudentProfileIdAndStatus(
            Long studentProfileId, MockInterviewStatus status);
}
