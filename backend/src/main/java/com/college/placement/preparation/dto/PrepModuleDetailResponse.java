package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepModuleDetailResponse {
    private Long id;
    private String code;
    private String title;
    private String description;
    private Integer sortOrder;
    private java.util.List<PrepTopicResponse> topics;
}