package com.college.placement.student;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_professionals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfessional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false, unique = true)
    private StudentProfile studentProfile;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    @Column(columnDefinition = "TEXT")
    private String projects;

    @Column(name = "resume_url", length = 500, columnDefinition = "VARCHAR(500)")
    private String resumeUrl;

    @Column(name = "github_url", length = 500, columnDefinition = "VARCHAR(500)")
    private String githubUrl;

    @Column(name = "linkedin_url", length = 500, columnDefinition = "VARCHAR(500)")
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 500, columnDefinition = "VARCHAR(500)")
    private String portfolioUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
