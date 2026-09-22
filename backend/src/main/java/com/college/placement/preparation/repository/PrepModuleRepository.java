package com.college.placement.preparation.repository;

import com.college.placement.preparation.domain.PrepModule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrepModuleRepository extends JpaRepository<PrepModule, Long> {

    List<PrepModule> findByActiveTrueOrderBySortOrderAsc();

    Optional<PrepModule> findByCode(String code);

    Optional<PrepModule> findByIdAndActiveTrue(Long id);

    @Query("SELECT m FROM PrepModule m WHERE m.active = true AND " +
            "(LOWER(m.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(m.code) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(m.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY m.sortOrder")
    List<PrepModule> searchActive(@Param("q") String q, Pageable pageable);
}