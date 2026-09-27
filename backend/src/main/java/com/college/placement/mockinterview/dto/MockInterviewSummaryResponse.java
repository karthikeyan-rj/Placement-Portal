package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row in the interview history list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewSummaryResponse {

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
}
