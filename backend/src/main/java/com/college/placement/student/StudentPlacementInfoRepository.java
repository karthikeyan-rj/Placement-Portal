package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPlacementInfoRepository extends JpaRepository<StudentPlacementInfo, Long> {

    StudentPlacementInfo findByStudentProfileId(Long studentProfileId);
}
