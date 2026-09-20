package com.college.placement.company;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByActiveTrue();

    long countByActiveTrue();

    boolean existsByNameIgnoreCase(String name);

    @Query(value = "SELECT c.* FROM companies c WHERE c.active = true AND " +
           "(:search IS NULL OR c.name ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM companies c WHERE c.active = true AND " +
           "(:search IS NULL OR c.name ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<Company> searchCompanies(@Param("search") String search, Pageable pageable);

    // Compact {id, name} option list for pickers/selects (no full DTO download, F3).
    @Query("SELECT c.id AS id, c.name AS name FROM Company c WHERE c.active = true ORDER BY c.name")
    List<CompanyOptionProjection> findActiveOptions();

    interface CompanyOptionProjection {
        Long getId();
        String getName();
    }
}
