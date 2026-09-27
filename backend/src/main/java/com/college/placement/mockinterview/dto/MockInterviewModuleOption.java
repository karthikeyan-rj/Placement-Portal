package com.college.placement.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A preparation module offered for a mode, with its active question count. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewModuleOption {

    private Long id;
    private String code;
    private String title;
    private Integer questionCount;
}
