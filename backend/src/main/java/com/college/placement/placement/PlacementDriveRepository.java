package com.college.placement.placement;

import com.college.placement.common.enums.PlacementDriveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, Long> {

    Page<PlacementDrive> findByStatusOrderByDriveDateDesc(PlacementDriveStatus status, Pageable pageable);

    Page<PlacementDrive> findAllByOrderByDriveDateDesc(Pageable pageable);
}
