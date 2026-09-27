package com.college.placement.preparation.repository;

import com.college.placement.common.enums.PrepDifficulty;
import com.college.placement.preparation.domain.PrepQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PrepQuestionRepository extends JpaRepository<PrepQuestion, Long> {

    List<PrepQuestion> findByTopicIdAndActiveTrueOrderBySortOrderAsc(Long topicId);

    @Query("SELECT q FROM PrepQuestion q JOIN FETCH q.topic t JOIN FETCH t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true AND " +
            "LOWER(q.question) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY m.sortOrder, t.sortOrder, q.sortOrder")
    List<PrepQuestion> searchActive(@Param("q") String q, Pageable pageable);

    // ---------------------------------------------------------------------
    // Mock Interview reuse (Phase 7N.2B).
    //
    // Mock Interview draws from this same bank rather than owning a copy. Only
    // questions whose question, topic and module are all `active` are eligible,
    // matching what PreparationService exposes to students.
    //
    // Callers pass the full set of PrepDifficulty values when no difficulty
    // filter is applied instead of passing null, which keeps the predicate a
    // plain IN (...) and avoids null-parameter type inference problems. This is
    // exhaustive: prep_questions.difficulty is constrained to exactly these
    // three values.
    // ---------------------------------------------------------------------

    @Query("SELECT q FROM PrepQuestion q JOIN FETCH q.topic t JOIN FETCH t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true " +
            "AND m.id IN :moduleIds AND q.difficulty IN :difficulties")
    List<PrepQuestion> findActiveForMockInterview(
            @Param("moduleIds") Collection<Long> moduleIds,
            @Param("difficulties") Collection<PrepDifficulty> difficulties);

    @Query("SELECT COUNT(q) FROM PrepQuestion q JOIN q.topic t JOIN t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true " +
            "AND m.id IN :moduleIds AND q.difficulty IN :difficulties")
    long countActiveForMockInterview(
            @Param("moduleIds") Collection<Long> moduleIds,
            @Param("difficulties") Collection<PrepDifficulty> difficulties);

    /** Active question count per module, used to report what each mode can offer. */
    @Query("SELECT m.id, COUNT(q) FROM PrepQuestion q " +
            "JOIN q.topic t JOIN t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true " +
            "GROUP BY m.id")
    List<Object[]> countActiveGroupedByModule();

    /** Active question count per module and difficulty, for option availability. */
    @Query("SELECT m.id, q.difficulty, COUNT(q) FROM PrepQuestion q " +
            "JOIN q.topic t JOIN t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true " +
            "GROUP BY m.id, q.difficulty")
    List<Object[]> countActiveGroupedByModuleAndDifficulty();
}