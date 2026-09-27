package com.college.placement.mockinterview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Saves (or clears) the student's free-text answer to one session question. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMockAnswerRequest {

    /** Null or blank clears the answer. */
    @NotNull(message = "studentAnswer is required (use an empty string to clear)")
    private String studentAnswer;
}
