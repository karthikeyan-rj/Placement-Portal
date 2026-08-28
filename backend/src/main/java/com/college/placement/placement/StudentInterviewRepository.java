package com.college.placement.placement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentInterviewRepository extends JpaRepository<StudentInterview, Long> {
    List<StudentInterview> findByStudentProfileIdOrderByInterviewDateDesc(Long studentProfileId);
    List<StudentInterview> findByPlacementDriveIdOrderByInterviewDateDesc(Long placementDriveId);
    Page<StudentInterview> findByStudentProfileUserIdOrderByInterviewDateDesc(Long userId, Pageable pageable);
}
