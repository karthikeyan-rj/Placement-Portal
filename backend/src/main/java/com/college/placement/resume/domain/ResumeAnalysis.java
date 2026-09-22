package com.college.placement.resume.domain;

import com.college.placement.resume.analysis.ContactPresence;
import com.college.placement.resume.analysis.RecommendationEntry;
import com.college.placement.resume.analysis.SectionCheck;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persisted result of a Resume Analyzer run, owned by a StudentProfile
 * (STUDENT and PR share the same profile, so history survives role flips).
 * Raw extracted resume text is never stored.
 */
@Entity
@Table(name = "resume_analyses", indexes = {
        @Index(name = "idx_resume_analyses_student_created",
                columnList = "student_profile_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "overall_readiness")
    private Integer overallReadiness;

    @Column(name = "ats_compatibility")
    private Integer atsCompatibility;

    @Column(name = "profile_score")
    private Integer profileScore;

    @Column(name = "content_score")
    private Integer contentScore;

    @Column(name = "impact_score")
    private Integer impactScore;

    @Column(name = "formatting_score")
    private Integer formattingScore;

    @Column(name = "links_score")
    private Integer linksScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_sections", columnDefinition = "jsonb")
    private List<SectionCheck> detectedSections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_checks", columnDefinition = "jsonb")
    private ContactPresence contactChecks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_skills", columnDefinition = "jsonb")
    private List<String> detectedSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<RecommendationEntry> recommendations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private List<String> warnings;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}