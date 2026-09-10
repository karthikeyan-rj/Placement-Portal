package com.college.placement.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PcDepartmentAssignmentRepository extends JpaRepository<PcDepartmentAssignment, Long> {

    List<PcDepartmentAssignment> findByActiveTrue();

    Optional<PcDepartmentAssignment> findByPcUserIdAndActiveTrue(Long pcUserId);

    List<PcDepartmentAssignment> findByDepartmentIdAndActiveTrue(Long departmentId);

    boolean existsByPcUserIdAndDepartmentIdAndActiveTrue(Long pcUserId, Long departmentId);

    long countByDepartmentIdAndActiveTrue(Long departmentId);
}
