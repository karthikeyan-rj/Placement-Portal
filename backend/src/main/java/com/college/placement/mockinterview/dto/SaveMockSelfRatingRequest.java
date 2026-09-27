package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Records the student's own confidence in an answer.
 *
 * <p>Allowed both during and after an interview: the review screen is precisely
 * where this is most useful. Only the rating is mutable once a session is
 * COMPLETED — the answer itself is not.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMockSelfRatingRequest {

    /** NEED_PRACTICE, PARTIALLY_CONFIDENT, CONFIDENT, or null to clear. */
    private String selfRating;
}
