package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StudentPlacementInfoRepository extends JpaRepository<StudentPlacementInfo, Long> {

    StudentPlacementInfo findByStudentProfileId(Long studentProfileId);

    List<StudentPlacementInfo> findAllByStudentProfileIdIn(Collection<Long> studentProfileIds);
}
