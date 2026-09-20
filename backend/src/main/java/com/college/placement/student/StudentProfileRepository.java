package com.college.placement.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    Optional<StudentProfile> findByRegisterNumber(String registerNumber);

    boolean existsByRegisterNumber(String registerNumber);

    boolean existsByUserId(Long userId);

    List<StudentProfile> findByUserDepartmentId(Long departmentId);

    @Query("SELECT DISTINCT sp FROM StudentProfile sp LEFT JOIN FETCH sp.user u LEFT JOIN FETCH u.department WHERE sp.id IN :profileIds")
    List<StudentProfile> findWithDetailsByIds(@Param("profileIds") Collection<Long> profileIds);

    @Query("SELECT DISTINCT sp FROM StudentProfile sp LEFT JOIN FETCH sp.user u LEFT JOIN FETCH u.department WHERE u.id IN :userIds")
    List<StudentProfile> findWithUserByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT DISTINCT sp FROM StudentProfile sp LEFT JOIN FETCH sp.user u LEFT JOIN FETCH u.department WHERE u.department.id = :departmentId")
    List<StudentProfile> findWithDetailsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT DISTINCT sp FROM StudentProfile sp LEFT JOIN FETCH sp.user u LEFT JOIN FETCH u.department ORDER BY sp.registerNumber")
    List<StudentProfile> findAllWithDetails();

    @Query(value = "SELECT sp.* FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE " +
           "u.department_id = :deptId AND u.active = true AND " +
           "(:search IS NULL OR sp.register_number ILIKE CONCAT('%', :search, '%') OR " +
           "u.name ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE " +
           "u.department_id = :deptId AND u.active = true AND " +
           "(:search IS NULL OR sp.register_number ILIKE CONCAT('%', :search, '%') OR " +
           "u.name ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<StudentProfile> searchByDepartment(@Param("deptId") Long deptId, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT sp.* FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE " +
           "u.active = true AND " +
           "(:search IS NULL OR sp.register_number ILIKE CONCAT('%', :search, '%') OR " +
           "u.name ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE " +
           "u.active = true AND " +
           "(:search IS NULL OR sp.register_number ILIKE CONCAT('%', :search, '%') OR " +
           "u.name ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<StudentProfile> searchAll(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(sp) FROM StudentProfile sp JOIN sp.user u WHERE u.department.id = :deptId AND u.active = true")
    long countByDepartmentId(@Param("deptId") Long deptId);

    long countByUserDepartmentId(Long departmentId);

    @Query("SELECT COUNT(sp) FROM StudentProfile sp JOIN sp.user u WHERE u.active = true")
    long countAll();

    String PROJECTION_COLUMNS =
            " sp.id AS id, sp.user_id AS userId, u.name AS userName, u.email AS userEmail, " +
            " sp.register_number AS registerNumber, sp.phone AS phone, " +
            " to_char(sp.date_of_birth, 'YYYY-MM-DD') AS dateOfBirth, " +
            " u.department_id AS departmentId, d.name AS departmentName, sp.batch AS batch, sp.section AS section, " +
            " sa.tenth_percentage AS tenthPercentage, sa.twelfth_percentage AS twelfthPercentage, " +
            " sa.diploma_percentage AS diplomaPercentage, sa.cgpa AS cgpa, " +
            " sa.active_backlogs AS activeBacklogs, sa.history_of_backlogs AS historyOfBacklogs, " +
            " spr.skills AS skills, spr.certifications AS certifications, spr.projects AS projects, " +
            " spr.resume_url AS resumeUrl, spr.github_url AS githubUrl, spr.linkedin_url AS linkedinUrl, spr.portfolio_url AS portfolioUrl, " +
            " spi.placement_interested AS placementInterested, spi.placement_status AS placementStatus, " +
            " spi.interviews_attended AS interviewsAttended, spco.id AS placedCompanyId, spco.name AS placedCompanyName, " +
            " spi.package_lpa AS packageLpa ";

    String PROJECTION_FROM =
            " FROM student_profiles sp " +
            " JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN departments d ON d.id = u.department_id " +
            " LEFT JOIN student_academics sa ON sa.student_profile_id = sp.id " +
            " LEFT JOIN student_professionals spr ON spr.student_profile_id = sp.id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id " +
            " LEFT JOIN companies spco ON spco.id = spi.placed_company_id ";

    // Canonical student population: active users holding a StudentProfile whose
    // role is STUDENT or PR (PRs are promoted students sharing the same profile).
    String PROJECTION_ROLE_GUARD = " u.role IN ('STUDENT','PR') ";

    String PROJECTION_WHERE =
            " WHERE u.active = true AND " + PROJECTION_ROLE_GUARD +
            " AND (:search IS NULL OR sp.register_number ILIKE CONCAT('%', :search, '%') OR u.name ILIKE CONCAT('%', :search, '%') OR u.email ILIKE CONCAT('%', :search, '%')) " +
            " AND (:role IS NULL OR u.role = :role) " +
            " AND (:placementInterested IS NULL OR spi.placement_interested = :placementInterested) " +
            " AND (:placementStatus IS NULL OR spi.placement_status = :placementStatus) ";

    // The LEFT JOIN to student_placement_info mirrors PROJECTION_FROM so filters
    // on placement columns behave identically in the main and count queries.
    // spi is 1:1 with student_profiles, so COUNT(*) stays correct.
    String PROJECTION_COUNT_SELECT =
            " SELECT COUNT(*) FROM student_profiles sp JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id ";

    String PROJECTION_WHERE_DEPT = PROJECTION_WHERE + " AND u.department_id = :deptId ";

    @Query(value = "SELECT " + PROJECTION_COLUMNS + PROJECTION_FROM + PROJECTION_WHERE +
            " ORDER BY sp.register_number",
            countQuery = PROJECTION_COUNT_SELECT + PROJECTION_WHERE,
            nativeQuery = true)
    Page<StudentListProjection> searchAllProjected(@Param("search") String search,
                                                   @Param("role") String role,
                                                   @Param("placementInterested") Boolean placementInterested,
                                                   @Param("placementStatus") String placementStatus,
                                                   Pageable pageable);

    @Query(value = "SELECT " + PROJECTION_COLUMNS + PROJECTION_FROM + " WHERE u.id = :userId",
            nativeQuery = true)
    Optional<StudentListProjection> findByUserIdProjected(@Param("userId") Long userId);

    @Query(value = "SELECT " + PROJECTION_COLUMNS + PROJECTION_FROM + PROJECTION_WHERE_DEPT +
            " ORDER BY sp.register_number",
            countQuery = PROJECTION_COUNT_SELECT + PROJECTION_WHERE_DEPT,
            nativeQuery = true)
    Page<StudentListProjection> searchByDepartmentProjected(@Param("deptId") Long deptId,
                                                            @Param("search") String search,
                                                            @Param("role") String role,
                                                            @Param("placementInterested") Boolean placementInterested,
                                                            @Param("placementStatus") String placementStatus,
                                                            Pageable pageable);

    // ---- Scale-correct aggregates (F3/F4): canonical population, no full-row downloads ----

    @Query(value = "SELECT COUNT(*) FROM student_profiles sp JOIN users u ON u.id = sp.user_id " +
            " WHERE u.active = true AND u.role IN ('STUDENT','PR') " +
            " AND (:deptId IS NULL OR u.department_id = :deptId)",
            nativeQuery = true)
    long countActivePopulation(@Param("deptId") Long deptId);

    @Query(value = "SELECT COUNT(*) FROM student_profiles sp JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id " +
            " WHERE u.active = true AND u.role IN ('STUDENT','PR') " +
            " AND spi.placement_interested = true " +
            " AND (:deptId IS NULL OR u.department_id = :deptId)",
            nativeQuery = true)
    long countPlacementInterested(@Param("deptId") Long deptId);

    @Query(value = "SELECT spi.placement_status AS status, COUNT(*) AS count FROM student_profiles sp " +
            " JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id " +
            " WHERE u.active = true AND u.role IN ('STUDENT','PR') " +
            " AND (:deptId IS NULL OR u.department_id = :deptId) " +
            " GROUP BY spi.placement_status",
            nativeQuery = true)
    List<StatusCountProjection> countByPlacementStatus(@Param("deptId") Long deptId);

    @Query(value = "SELECT u.department_id AS departmentId, d.name AS departmentName, " +
            " COUNT(*) AS studentCount, " +
            " COUNT(*) FILTER (WHERE spi.placement_interested = true) AS interestedCount, " +
            " COUNT(*) FILTER (WHERE spi.placement_status = 'PLACED') AS placedCount " +
            " FROM student_profiles sp JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN departments d ON d.id = u.department_id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id " +
            " WHERE u.active = true AND u.role IN ('STUDENT','PR') " +
            " AND (:deptId IS NULL OR u.department_id = :deptId) " +
            " GROUP BY u.department_id, d.name ORDER BY d.name",
            nativeQuery = true)
    List<DepartmentAggregateProjection> summarizeByDepartment(@Param("deptId") Long deptId);

    @Query(value = "SELECT sp.batch AS batch, " +
            " COUNT(*) AS studentCount, " +
            " COUNT(*) FILTER (WHERE spi.placement_interested = true) AS interestedCount, " +
            " COUNT(*) FILTER (WHERE spi.placement_status = 'PLACED') AS placedCount " +
            " FROM student_profiles sp JOIN users u ON u.id = sp.user_id " +
            " LEFT JOIN student_placement_info spi ON spi.student_profile_id = sp.id " +
            " WHERE u.active = true AND u.role IN ('STUDENT','PR') " +
            " AND (:deptId IS NULL OR u.department_id = :deptId) " +
            " GROUP BY sp.batch ORDER BY sp.batch",
            nativeQuery = true)
    List<BatchAggregateProjection> summarizeByBatch(@Param("deptId") Long deptId);

    interface StatusCountProjection {
        String getStatus();
        long getCount();
    }

    interface DepartmentAggregateProjection {
        Long getDepartmentId();
        String getDepartmentName();
        long getStudentCount();
        long getInterestedCount();
        long getPlacedCount();
    }

    interface BatchAggregateProjection {
        String getBatch();
        long getStudentCount();
        long getInterestedCount();
        long getPlacedCount();
    }

    interface StudentListProjection {
        Long getId();
        Long getUserId();
        String getUserName();
        String getUserEmail();
        String getRegisterNumber();
        String getPhone();
        String getDateOfBirth();
        Long getDepartmentId();
        String getDepartmentName();
        String getBatch();
        String getSection();
        java.math.BigDecimal getTenthPercentage();
        java.math.BigDecimal getTwelfthPercentage();
        java.math.BigDecimal getDiplomaPercentage();
        java.math.BigDecimal getCgpa();
        Integer getActiveBacklogs();
        Integer getHistoryOfBacklogs();
        String getSkills();
        String getCertifications();
        String getProjects();
        String getResumeUrl();
        String getGithubUrl();
        String getLinkedinUrl();
        String getPortfolioUrl();
        Boolean getPlacementInterested();
        String getPlacementStatus();
        Integer getInterviewsAttended();
        Long getPlacedCompanyId();
        String getPlacedCompanyName();
        java.math.BigDecimal getPackageLpa();
    }
}
