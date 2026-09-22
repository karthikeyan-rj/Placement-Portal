package com.college.placement.resume.repository;

import com.college.placement.resume.domain.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    List<ResumeAnalysis> findTop20ByStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId);

    Optional<ResumeAnalysis> findByIdAndStudentProfileId(Long id, Long studentProfileId);
}