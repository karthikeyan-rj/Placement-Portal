package com.college.placement.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrAssignmentRepository extends JpaRepository<PrAssignment, Long> {

    List<PrAssignment> findByActiveTrue();

    Optional<PrAssignment> findByStudentProfileIdAndActiveTrue(Long studentProfileId);

    List<PrAssignment> findByDepartmentIdAndActiveTrue(Long departmentId);

    boolean existsByStudentProfileIdAndDepartmentIdAndActiveTrue(Long studentProfileId, Long departmentId);
}
