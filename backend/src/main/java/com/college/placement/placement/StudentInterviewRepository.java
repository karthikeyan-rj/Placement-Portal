package com.college.placement.placement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentInterviewRepository extends JpaRepository<StudentInterview, Long> {

    @EntityGraph(attributePaths = {
            "studentProfile",
            "studentProfile.user",
            "placementDrive",
            "placementDrive.company"
    })
    List<StudentInterview> findByStudentProfileIdOrderByInterviewDateDesc(Long studentProfileId);

    @EntityGraph(attributePaths = {
            "studentProfile",
            "studentProfile.user",
            "placementDrive",
            "placementDrive.company"
    })
    List<StudentInterview> findByPlacementDriveIdOrderByInterviewDateDesc(Long placementDriveId);

    @EntityGraph(attributePaths = {
            "studentProfile",
            "studentProfile.user",
            "placementDrive",
            "placementDrive.company"
    })
    Page<StudentInterview> findByStudentProfileUserIdOrderByInterviewDateDesc(Long userId, Pageable pageable);
}
