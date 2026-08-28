package com.college.placement.placement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlacementRecordRepository extends JpaRepository<PlacementRecord, Long> {
    List<PlacementRecord> findByStudentProfileIdOrderByPlacementDateDesc(Long studentProfileId);
    List<PlacementRecord> findByCompanyId(Long companyId);

    @Query("SELECT COUNT(pr) FROM PlacementRecord pr WHERE pr.status = 'PLACED'")
    long countPlacedStudents();

    @Query("SELECT COUNT(pr) FROM PlacementRecord pr WHERE pr.studentProfile.user.department.id = :deptId AND pr.status = 'PLACED'")
    long countPlacedByDepartment(@Param("deptId") Long deptId);
}
