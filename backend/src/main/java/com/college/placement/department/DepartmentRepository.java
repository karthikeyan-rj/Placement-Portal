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

    // Aggregated per-department counts computed entirely in the database (F3).
    // studentCount counts active users holding a StudentProfile with role
    // STUDENT or PR; pcCount/prCount count active PCs/PRs.
    @Query(value = "SELECT d.id AS departmentId, d.name AS departmentName, d.active AS active, " +
            " pc.max_prs AS prLimit, " +
            " COUNT(DISTINCT sp.id) FILTER (WHERE u.active = true AND u.role IN ('STUDENT','PR')) AS studentCount, " +
            " COUNT(DISTINCT u.id) FILTER (WHERE u.active = true AND u.role = 'PC') AS pcCount, " +
            " COUNT(DISTINCT u.id) FILTER (WHERE u.active = true AND u.role = 'PR') AS prCount " +
            " FROM departments d " +
            " LEFT JOIN pr_config pc ON pc.department_id = d.id " +
            " LEFT JOIN users u ON u.department_id = d.id " +
            " LEFT JOIN student_profiles sp ON sp.user_id = u.id " +
            " GROUP BY d.id, d.name, d.active, pc.max_prs " +
            " ORDER BY d.name",
            nativeQuery = true)
    List<DepartmentAggregateProjection> findAllAggregates();

    interface DepartmentAggregateProjection {
        Long getDepartmentId();
        String getDepartmentName();
        boolean getActive();
        Integer getPrLimit();
        long getStudentCount();
        long getPcCount();
        long getPrCount();
    }
}
