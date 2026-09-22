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
public class PrepTopicDetailResponse {
    private Long id;
    private String code;
    private String title;
    private String description;
    private String studyGuide;
    private Integer estimatedMinutes;
    private Integer sortOrder;
    private Boolean completed;
    private String confidence;
    private Long moduleId;
    private String moduleCode;
    private String moduleTitle;
    private List<PrepQuestionResponse> questions;
}