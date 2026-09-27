package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tally of the student's own ratings.
 *
 * <p>Named "selfAssessment" rather than "score" on purpose: these are the
 * student's judgements, not a measurement of correctness.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewSelfAssessmentSummary {

    private Integer confident;
    private Integer partiallyConfident;
    private Integer needPractice;
    private Integer unrated;
    private Integer ratedCount;
}
