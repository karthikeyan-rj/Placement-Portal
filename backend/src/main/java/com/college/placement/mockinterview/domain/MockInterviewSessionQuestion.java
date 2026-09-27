package com.college.placement.mockinterview.domain;

import com.college.placement.common.enums.MockSelfRating;
import com.college.placement.preparation.domain.PrepQuestion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One question inside a session, with the student's answer and self-rating.
 *
 * <p>Stores a reference to the shared {@link PrepQuestion} rather than a copy of
 * the question, its answer guide, or any options/correct-answer data. The
 * preparation bank is descriptive only, so there is deliberately no
 * {@code isCorrect} field here.
 */
@Entity
@Table(name = "mock_interview_session_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_mock_session_question_position",
                        columnNames = {"session_id", "position"}),
                @UniqueConstraint(name = "uq_mock_session_question_question",
                        columnNames = {"session_id", "question_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockInterviewSessionQuestion {

    /** Documented ceiling for a free-text answer; enforced by check constraint and service. */
    public static final int MAX_ANSWER_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MockInterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private PrepQuestion question;

    /** 1-based position that fixes the interview order for the life of the session. */
    @Column(nullable = false)
    private Integer position;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_rating", length = 30)
    private MockSelfRating selfRating;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** A question counts as answered only when it has real, non-blank content. */
    public boolean isAnswered() {
        return studentAnswer != null && !studentAnswer.isBlank();
    }
}
