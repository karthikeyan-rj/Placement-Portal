package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StudentAcademicRepository extends JpaRepository<StudentAcademic, Long> {

    StudentAcademic findByStudentProfileId(Long studentProfileId);

    List<StudentAcademic> findAllByStudentProfileIdIn(Collection<Long> studentProfileIds);
}
