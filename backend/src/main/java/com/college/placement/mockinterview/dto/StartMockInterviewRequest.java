package com.college.placement.mockinterview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to start a session.
 *
 * <p>Deliberately contains no student identifier: the owner is always resolved
 * from the JWT on the server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartMockInterviewRequest {

    @NotNull(message = "interviewType is required")
    private String interviewType;

    /**
     * Optional narrowing of the mode's modules. When omitted or empty, every
     * active module in the mode is used. Unknown or inactive ids are rejected
     * rather than silently ignored.
     */
    private List<Long> moduleIds;

    @NotNull(message = "difficulty is required")
    private String difficulty;

    @NotNull(message = "questionCount is required")
    @Min(value = 1, message = "questionCount must be at least 1")
    @Max(value = 20, message = "questionCount must be 20 or fewer")
    private Integer questionCount;

    /**
     * Optional deterministic selection seed.
     *
     * <p>Exists so automated tests get a reproducible question set. When omitted,
     * selection is randomised per request. It has no effect on anything except
     * which questions are drawn — the chosen set is persisted immediately, so a
     * refresh never reshuffles an interview either way.
     */
    private Long randomSeed;
}
