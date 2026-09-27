package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A session with its questions in fixed order.
 *
 * <p>{@code elapsedSeconds} is computed on the server from {@code startedAt} at
 * request time, so the interview clock survives a page refresh or a reopened tab.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewSessionResponse {

    private Long id;
    private String interviewType;
    private String interviewTypeLabel;
    private String difficulty;
    private String status;
    private Integer questionCount;
    private Integer answeredCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationSeconds;
    private Long elapsedSeconds;
    private Boolean hasObjectiveQuestions;
    private List<MockInterviewQuestionView> questions;
}
