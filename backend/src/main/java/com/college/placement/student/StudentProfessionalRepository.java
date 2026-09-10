package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StudentProfessionalRepository extends JpaRepository<StudentProfessional, Long> {

    StudentProfessional findByStudentProfileId(Long studentProfileId);

    List<StudentProfessional> findAllByStudentProfileIdIn(Collection<Long> studentProfileIds);
}
