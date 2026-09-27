package com.college.placement.user;

import com.college.placement.common.enums.Role;
import com.college.placement.placement.dto.DriveRecipientProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "department")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "department")
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findUsersByIds(@Param("ids") Collection<Long> ids);

    boolean existsByEmail(String email);

    /** Batch lookup used by the PO student CSV import to avoid per-row queries. */
    @Query("SELECT u.email FROM User u WHERE u.email IN :emails")
    List<String> findEmailsIn(@Param("emails") Collection<String> emails);

    List<User> findByRole(Role role);

    List<User> findByRoleAndDepartmentId(Role role, Long departmentId);

    List<User> findByDepartmentId(Long departmentId);

    List<User> findByActiveTrue();

    @Query(value = "SELECT u.* FROM users u WHERE u.active = true AND " +
           "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
           "u.email ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM users u WHERE u.active = true AND " +
           "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
           "u.email ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u WHERE u.department_id = :deptId AND u.active = true AND " +
           "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
           "u.email ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM users u WHERE u.department_id = :deptId AND u.active = true AND " +
           "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
           "u.email ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<User> searchUsersByDepartment(@Param("deptId") Long deptId, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u WHERE u.active = true AND " +
            "(:deptId IS NULL OR u.department_id = :deptId) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
            "u.email ILIKE CONCAT('%', :search, '%'))",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.active = true AND " +
            "(:deptId IS NULL OR u.department_id = :deptId) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:search IS NULL OR u.name ILIKE CONCAT('%', :search, '%') OR " +
            "u.email ILIKE CONCAT('%', :search, '%'))",
            nativeQuery = true)
    Page<User> searchUsersScoped(@Param("deptId") Long deptId,
                                  @Param("role") String role,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.active = true")
    long countByRole(@Param("role") Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.department.id = :deptId AND u.active = true")
    long countByRoleAndDepartment(@Param("role") Role role, @Param("deptId") Long deptId);

    @Query(value = "SELECT COUNT(*) FROM users u JOIN student_profiles sp ON sp.user_id = u.id " +
           "WHERE u.active = true AND u.role IN ('STUDENT','PR')", nativeQuery = true)
    long countActiveStudentPopulation();

    long countByActiveTrue();

    /**
     * Set-based equivalent of PlacementDriveService.checkEligibility for a drive with no
     * criteria: every active STUDENT/PR that owns a StudentProfile and has a usable email.
     */
    @Query(value = "SELECT u.id AS userId, u.email AS email FROM users u "
            + "JOIN student_profiles sp ON sp.user_id = u.id "
            + "WHERE u.active = true AND u.role IN ('STUDENT','PR') "
            + "AND u.email IS NOT NULL AND BTRIM(u.email) <> '' "
            + "ORDER BY u.id", nativeQuery = true)
    List<DriveRecipientProjection> findDriveRecipientsWithoutCriteria();

    /**
     * Set-based equivalent of PlacementDriveService.checkEligibility when criteria exist.
     * Mirrors the single-student rules exactly, including the "missing value does not fail
     * the check" semantics for CGPA and active backlogs.
     */
    @Query(value = "SELECT u.id AS userId, u.email AS email FROM users u "
            + "JOIN student_profiles sp ON sp.user_id = u.id "
            + "JOIN student_academics sa ON sa.student_profile_id = sp.id "
            + "WHERE u.active = true AND u.role IN ('STUDENT','PR') "
            + "AND u.email IS NOT NULL AND BTRIM(u.email) <> '' "
            + "AND u.department_id IS NOT NULL "
            + "AND (:allDepartments = true OR u.department_id IN :departmentIds) "
            + "AND (:minCgpa IS NULL OR sa.cgpa IS NULL OR sa.cgpa >= :minCgpa) "
            + "AND (:maxBacklogs IS NULL OR sa.active_backlogs IS NULL OR sa.active_backlogs <= :maxBacklogs) "
            + "ORDER BY u.id", nativeQuery = true)
    List<DriveRecipientProjection> findDriveRecipientsWithCriteria(@Param("allDepartments") boolean allDepartments,
                                                                   @Param("departmentIds") List<Long> departmentIds,
                                                                   @Param("minCgpa") BigDecimal minCgpa,
                                                                   @Param("maxBacklogs") Integer maxBacklogs);
}
