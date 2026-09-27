package com.college.placement.studentimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowPreview {
    private Integer rowNumber;
    private String email;
    private String name;
    private String registerNumber;
    private String department;
    private ImportRowStatus status;
    /** Human-readable reason when status is not READY. Null for READY rows. */
    private String error;
}
