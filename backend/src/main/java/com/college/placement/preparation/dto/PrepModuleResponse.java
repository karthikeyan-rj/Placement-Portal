package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepModuleResponse {
    private Long id;
    private String code;
    private String title;
    private String description;
    private int topicCount;
    private int completedTopics;
    private double progressPercent;
}