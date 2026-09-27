package com.college.placement.placement;

import com.college.placement.placement.dto.CreateEligibilityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EligibilityCriteriaRepository extends JpaRepository<EligibilityCriteria, Long> {

    Optional<EligibilityCriteria> findByPlacementDriveId(Long placementDriveId);

    @EntityGraph(attributePaths = {"allowedDepartments"})
    List<EligibilityCriteria> findAllByPlacementDriveIdIn(Collection<Long> placementDriveIds);

    /**
     * Reads the allowed department ids for a drive without materialising the lazy
     * {@code allowedDepartments} collection. Callers include the outbox worker and the
     * after-commit drive listener, which run with no Hibernate session bound, so touching
     * the entity collection there would fail with a LazyInitializationException.
     *
     * @return allowed department ids, empty when the drive is unrestricted
     */
    @Query("SELECT d.id FROM EligibilityCriteria c JOIN c.allowedDepartments d "
            + "WHERE c.placementDrive.id = :driveId")
    List<Long> findAllowedDepartmentIdsByDriveId(@Param("driveId") Long driveId);
}
