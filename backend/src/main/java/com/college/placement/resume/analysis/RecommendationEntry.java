package com.college.placement.resume.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single prioritised improvement recommendation. {@code level} is one of
 * HIGH / MEDIUM / LOW. Text is always advisory and never fabricates metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationEntry {

    private String level;
    private String text;
}