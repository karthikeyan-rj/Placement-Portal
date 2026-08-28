package com.college.placement.placement;

import com.college.placement.common.enums.InterviewStatus;
import com.college.placement.student.StudentProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_drive_id", nullable = false)
    private PlacementDrive placementDrive;

    @Column(name = "round_name", nullable = false, length = 255, columnDefinition = "VARCHAR(255)")
    private String roundName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status;

    @Builder.Default
    private Boolean attended = false;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
