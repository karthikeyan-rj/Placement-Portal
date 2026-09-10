package com.college.placement.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrConfigRepository extends JpaRepository<PrConfig, Long> {

    Optional<PrConfig> findByDepartmentId(Long departmentId);

    List<PrConfig> findAllByDepartmentIdIn(Collection<Long> departmentIds);
}
