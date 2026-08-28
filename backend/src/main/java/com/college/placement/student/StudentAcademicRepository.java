package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentAcademicRepository extends JpaRepository<StudentAcademic, Long> {

    StudentAcademic findByStudentProfileId(Long studentProfileId);
}
