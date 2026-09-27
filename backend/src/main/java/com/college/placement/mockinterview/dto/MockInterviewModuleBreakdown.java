package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewModuleBreakdown {

    private Long moduleId;
    private String moduleCode;
    private String moduleTitle;
    private Integer questionCount;
    private Integer answeredCount;
}
