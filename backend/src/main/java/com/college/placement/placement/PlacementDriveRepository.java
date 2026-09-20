package com.college.placement.placement;

import com.college.placement.common.enums.PlacementDriveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, Long> {

    @EntityGraph(attributePaths = {"company"})
    Page<PlacementDrive> findByStatusOrderByDriveDateDesc(PlacementDriveStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"company"})
    Page<PlacementDrive> findAllByOrderByDriveDateDesc(Pageable pageable);

    @Query("SELECT COUNT(pd) FROM PlacementDrive pd WHERE pd.status NOT IN :activeStatuses")
    long countActive(@Param("activeStatuses") Collection<PlacementDriveStatus> activeStatuses);

    @Query("SELECT COUNT(pd) FROM PlacementDrive pd WHERE pd.status = :status")
    long countByStatus(@Param("status") PlacementDriveStatus status);
}
