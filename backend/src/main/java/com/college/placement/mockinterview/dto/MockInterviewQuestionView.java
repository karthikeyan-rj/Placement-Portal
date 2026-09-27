package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A question inside a session, joined to its module/topic for display.
 *
 * <p>{@code referenceAnswer} is populated from the preparation question's
 * {@code answer_guide} <em>only</em> once the session is COMPLETED. During an
 * interview it stays null so that practising is not undermined.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewQuestionView {

    private Long sessionQuestionId;
    private Integer position;
    private Long questionId;
    private String question;
    private String difficulty;
    private Long moduleId;
    private String moduleCode;
    private String moduleTitle;
    private Long topicId;
    private String topicTitle;

    private String studentAnswer;
    private String selfRating;
    private Boolean answered;
    private LocalDateTime answeredAt;

    /** Null while the session is IN_PROGRESS. */
    private String referenceAnswer;
}
