package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Everything the setup screen needs, derived from live preparation content. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewOptionsResponse {

    private java.util.List<MockInterviewModeOption> modes;

    /** Question counts the UI may offer. */
    private java.util.List<Integer> allowedQuestionCounts;

    /** Ceiling on a single free-text answer. */
    private Integer maxAnswerLength;

    /** True when the question bank contains no objective items (it does not today). */
    private Boolean objectiveScoringSupported;
}
