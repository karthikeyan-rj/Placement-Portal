package com.college.placement.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Department> findByActiveTrue();

    List<Department> findAllByOrderByNameAsc();

    @Query("SELECT d, pc FROM Department d LEFT JOIN PrConfig pc ON pc.department = d ORDER BY d.name")
    List<Object[]> findAllWithPrConfig();

    @Query("SELECT d, pc FROM Department d LEFT JOIN PrConfig pc ON pc.department = d WHERE d.active = true ORDER BY d.name")
    List<Object[]> findActiveWithPrConfig();
}
