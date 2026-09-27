package com.college.placement.mockinterview.repository;

import com.college.placement.common.enums.MockInterviewStatus;
import com.college.placement.mockinterview.domain.MockInterviewSessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MockInterviewSessionQuestionRepository
        extends JpaRepository<MockInterviewSessionQuestion, Long> {

    /**
     * A whole session in fixed order, with question, topic and module fetched in
     * the same statement. This is the single read used by the interview screen,
     * the review screen and the results breakdown, so none of them can degrade
     * into an N+1 over questions.
     */
    @Query("SELECT sq FROM MockInterviewSessionQuestion sq " +
            "JOIN FETCH sq.question q " +
            "JOIN FETCH q.topic t " +
            "JOIN FETCH t.module m " +
            "WHERE sq.session.id = :sessionId " +
            "ORDER BY sq.position")
    List<MockInterviewSessionQuestion> findSessionDetail(@Param("sessionId") Long sessionId);

    /** Ownership + membership check in one statement, for answering a question. */
    @Query("SELECT sq FROM MockInterviewSessionQuestion sq " +
            "JOIN FETCH sq.question q " +
            "JOIN FETCH q.topic t " +
            "JOIN FETCH t.module m " +
            "WHERE sq.id = :sessionQuestionId " +
            "AND sq.session.id = :sessionId " +
            "AND sq.session.studentProfile.id = :studentProfileId")
    Optional<MockInterviewSessionQuestion> findOwnedSessionQuestion(
            @Param("sessionQuestionId") Long sessionQuestionId,
            @Param("sessionId") Long sessionId,
            @Param("studentProfileId") Long studentProfileId);

    /**
     * Every question this student has already been shown in a real (non-abandoned)
     * interview, most recent session first. Abandoned sessions are excluded: a
     * dropped practice run must not count as exposure.
     *
     * <p>The status is passed as a parameter rather than inlined as an enum
     * literal so the query stays portable and type-safe.
     */
    @Query("SELECT sq.question.id FROM MockInterviewSessionQuestion sq " +
            "JOIN sq.session s " +
            "WHERE s.studentProfile.id = :studentProfileId " +
            "AND s.status <> :excludedStatus " +
            "ORDER BY s.startedAt DESC")
    List<Long> findQuestionIdsSeenByStudent(
            @Param("studentProfileId") Long studentProfileId,
            @Param("excludedStatus") MockInterviewStatus excludedStatus);

    /**
     * Answered count per session for a whole page of history, in one statement.
     * A blank string counts as unanswered, matching {@code isAnswered()}.
     */
    @Query("SELECT sq.session.id, " +
            "SUM(CASE WHEN sq.studentAnswer IS NOT NULL AND TRIM(sq.studentAnswer) <> '' THEN 1 ELSE 0 END) " +
            "FROM MockInterviewSessionQuestion sq " +
            "WHERE sq.session.id IN :sessionIds " +
            "GROUP BY sq.session.id")
    List<Object[]> countAnsweredBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    List<MockInterviewSessionQuestion> findBySessionIdOrderByPosition(Long sessionId);
}
