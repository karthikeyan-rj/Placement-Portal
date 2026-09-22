package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepModuleProgressResponse {
    private Long moduleId;
    private String moduleCode;
    private String moduleTitle;
    private int totalTopics;
    private int completedTopics;
    private double progressPercent;
}