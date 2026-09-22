package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepSummaryResponse {
    private int totalTopics;
    private int completedTopics;
    private double completionPercent;
    private int lowConfidenceTopics;
    private int mediumConfidenceTopics;
    private int highConfidenceTopics;
    private List<PrepModuleProgressResponse> moduleProgress;
}