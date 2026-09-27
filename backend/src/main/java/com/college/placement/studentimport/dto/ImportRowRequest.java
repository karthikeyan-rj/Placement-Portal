package com.college.placement.studentimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowRequest {
    /** Original CSV line number (1-based, header = line 1) for error reporting. */
    private Integer rowNumber;
    private String email;
    private String name;
    private String registerNumber;
    private String department;
}
