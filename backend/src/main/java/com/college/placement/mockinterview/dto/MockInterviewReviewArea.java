package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A topic worth revisiting, derived only from the student's own
 * NEED_PRACTICE / unanswered signals — never from an invented correctness score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewReviewArea {

    private String moduleCode;
    private String topicTitle;
    private String reason;
    private Integer needPracticeCount;
    private Integer unansweredCount;
}
