package com.college.placement.resume.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Lightweight entry shown in the Recent Analyses history list. */
@Data
@Builder
public class ResumeAnalysisSummaryResponse {

    private Long id;
    private String fileName;
    private Integer pageCount;
    private Integer readinessScore;
    private LocalDateTime createdAt;
}