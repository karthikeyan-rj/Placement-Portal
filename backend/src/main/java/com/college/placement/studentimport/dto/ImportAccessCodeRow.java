package com.college.placement.studentimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the one-time registration-codes download. The plaintext
 * accessCode exists only in this in-memory response — it is never persisted,
 * never logged and never re-served.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAccessCodeRow {
    private Integer rowNumber;
    private String name;
    private String email;
    private String registerNumber;
    private String department;
    private String accessCode;
}
