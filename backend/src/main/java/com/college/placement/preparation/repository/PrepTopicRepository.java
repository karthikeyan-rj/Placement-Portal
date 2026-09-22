package com.college.placement.preparation.repository;

import com.college.placement.preparation.domain.PrepTopic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrepTopicRepository extends JpaRepository<PrepTopic, Long> {

    List<PrepTopic> findByModuleIdAndActiveTrueOrderBySortOrderAsc(Long moduleId);

    Optional<PrepTopic> findByCode(String code);

    Optional<PrepTopic> findByIdAndActiveTrue(Long id);

    @Query("SELECT t.module.id AS moduleId, COUNT(t) AS cnt FROM PrepTopic t " +
            "WHERE t.active = true AND t.module.active = true GROUP BY t.module.id")
    List<TopicCountProjection> countActiveTopicsGroupedByModule();

    @Query("SELECT t FROM PrepTopic t JOIN FETCH t.module m " +
            "WHERE t.active = true AND m.active = true AND " +
            "(LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY m.sortOrder, t.sortOrder")
    List<PrepTopic> searchActive(@Param("q") String q, Pageable pageable);

    interface TopicCountProjection {
        Long getModuleId();
        long getCnt();
    }
}