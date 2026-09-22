package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepQuestionResponse {
    private Long id;
    private String question;
    private String answerGuide;
    private String difficulty;
    private Integer sortOrder;
}