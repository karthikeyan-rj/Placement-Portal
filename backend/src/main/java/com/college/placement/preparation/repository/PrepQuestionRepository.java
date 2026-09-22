package com.college.placement.preparation.repository;

import com.college.placement.preparation.domain.PrepQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrepQuestionRepository extends JpaRepository<PrepQuestion, Long> {

    List<PrepQuestion> findByTopicIdAndActiveTrueOrderBySortOrderAsc(Long topicId);

    @Query("SELECT q FROM PrepQuestion q JOIN FETCH q.topic t JOIN FETCH t.module m " +
            "WHERE q.active = true AND t.active = true AND m.active = true AND " +
            "LOWER(q.question) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY m.sortOrder, t.sortOrder, q.sortOrder")
    List<PrepQuestion> searchActive(@Param("q") String q, Pageable pageable);
}