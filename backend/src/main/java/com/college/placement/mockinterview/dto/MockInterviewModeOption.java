package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** One interview mode with the modules it can draw on and how many questions exist. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewModeOption {

    private String code;
    private String label;
    private String description;
    private List<MockInterviewModuleOption> modules;

    /**
     * Active question availability keyed by difficulty filter value, plus
     * {@code ALL} for the unfiltered total. Lets the setup screen tell the
     * student "only N available" instead of silently padding the session.
     */
    private Map<String, Integer> availableByDifficulty;
}
