package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentProfessionalRepository extends JpaRepository<StudentProfessional, Long> {

    StudentProfessional findByStudentProfileId(Long studentProfileId);
}
