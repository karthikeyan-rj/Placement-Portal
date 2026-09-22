package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepProgressResponse {
    private Long topicId;
    private String topicCode;
    private String topicTitle;
    private Long moduleId;
    private String moduleCode;
    private String moduleTitle;
    private Boolean completed;
    private String confidence;
    private String updatedAt;
}