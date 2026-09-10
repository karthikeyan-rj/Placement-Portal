package com.college.placement.placement;

import com.college.placement.placement.dto.CreateEligibilityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EligibilityCriteriaRepository extends JpaRepository<EligibilityCriteria, Long> {

    Optional<EligibilityCriteria> findByPlacementDriveId(Long placementDriveId);

    @EntityGraph(attributePaths = {"allowedDepartments"})
    List<EligibilityCriteria> findAllByPlacementDriveIdIn(Collection<Long> placementDriveIds);
}
