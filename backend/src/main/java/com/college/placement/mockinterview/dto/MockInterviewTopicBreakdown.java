package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewTopicBreakdown {

    private Long topicId;
    private String topicCode;
    private String topicTitle;
    private String moduleCode;
    private Integer questionCount;
    private Integer answeredCount;
    private Integer needPracticeCount;
}
