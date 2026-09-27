package com.college.placement.mockinterview.domain;

import com.college.placement.common.enums.MockDifficultyFilter;
import com.college.placement.common.enums.MockInterviewStatus;
import com.college.placement.common.enums.MockInterviewType;
import com.college.placement.student.StudentProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One Mock Interview attempt.
 *
 * <p>Ownership is always derived from the authenticated student's
 * {@link StudentProfile}; it is never accepted from the request body.
 *
 * <p>The session stores no question content. The chosen questions and their fixed
 * order live in {@link MockInterviewSessionQuestion}, and the question text is read
 * live from the preparation domain.
 */
@Entity
@Table(name = "mock_interview_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockInterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 30)
    private MockInterviewType interviewType;

    /** The filter that was chosen, which may be {@link MockDifficultyFilter#MIXED}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MockDifficultyFilter difficulty;

    /** Number of questions actually assigned. Never inflated by repeating questions. */
    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MockInterviewStatus status = MockInterviewStatus.IN_PROGRESS;

    /** Interview clock origin. The elapsed timer is derived from this, so a refresh never resets it. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isInProgress() {
        return status == MockInterviewStatus.IN_PROGRESS;
    }
}
