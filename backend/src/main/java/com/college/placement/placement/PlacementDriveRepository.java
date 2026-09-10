package com.college.placement.placement;

import com.college.placement.common.enums.PlacementDriveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, Long> {

    @EntityGraph(attributePaths = {"company"})
    Page<PlacementDrive> findByStatusOrderByDriveDateDesc(PlacementDriveStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"company"})
    Page<PlacementDrive> findAllByOrderByDriveDateDesc(Pageable pageable);
}
