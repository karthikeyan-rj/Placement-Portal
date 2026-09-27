package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outcome of a completed session.
 *
 * <p>There is intentionally no overall percentage or letter grade. The
 * preparation bank is descriptive only, so an aggregate "score" would be
 * fabricated. What is reported instead is coverage (what was practised), what the
 * student answered, and the student's own self-assessment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewResultResponse {

    private Long id;
    private String interviewType;
    private String interviewTypeLabel;
    private String difficulty;
    private String status;
    private Integer questionCount;
    private Integer answeredCount;
    private Integer unansweredCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationSeconds;

    /** Always false for the current bank; kept explicit so the UI never implies a score. */
    private Boolean objectiveScoringSupported;

    private List<MockInterviewModuleBreakdown> moduleBreakdown;
    private List<MockInterviewTopicBreakdown> topicBreakdown;
    private MockInterviewSelfAssessmentSummary selfAssessment;
    private List<MockInterviewReviewArea> areasToReview;
}
