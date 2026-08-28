package com.college.placement.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    Optional<StudentProfile> findByRegisterNumber(String registerNumber);

    boolean existsByRegisterNumber(String registerNumber);

    boolean existsByUserId(Long userId);

    List<StudentProfile> findByUserDepartmentId(Long departmentId);

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
}
