package com.college.placement.user;

import com.college.placement.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "department")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

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

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.active = true")
    long countByRole(@Param("role") Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.department.id = :deptId AND u.active = true")
    long countByRoleAndDepartment(@Param("role") Role role, @Param("deptId") Long deptId);

    long countByActiveTrue();
}
