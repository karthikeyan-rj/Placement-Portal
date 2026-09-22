package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepTopicResponse {
    private Long id;
    private String code;
    private String title;
    private String description;
    private String studyGuide;
    private Integer estimatedMinutes;
    private Integer sortOrder;
    private Boolean completed;
    private String confidence;
}