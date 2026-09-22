package com.college.placement.resume.dto;

import com.college.placement.resume.analysis.ContactPresence;
import com.college.placement.resume.analysis.RecommendationEntry;
import com.college.placement.resume.analysis.SectionCheck;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured analysis result returned to the student. Contains the derived
 * checks and scores only — never the extracted resume text, email or phone.
 */
@Data
@Builder
public class ResumeAnalysisResponse {

    private Long id;
    private String fileName;
    private Long fileSize;
    private Integer pageCount;
    private Integer readinessScore;
    private Integer atsCompatibility;
    private CategoryScores categoryScores;
    private List<SectionCheck> sections;
    private ContactPresence contactChecks;
    private List<String> detectedSkills;
    private List<String> warnings;
    private List<RecommendationEntry> recommendations;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class CategoryScores {
        private Integer profileCompleteness;
        private Integer contentQuality;
        private Integer impact;
        private Integer formatting;
        private Integer professionalLinks;
    }
}