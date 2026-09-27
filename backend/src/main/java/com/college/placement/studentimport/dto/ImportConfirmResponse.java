package com.college.placement.studentimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportConfirmResponse {
    /** Rows newly authorized in this run (access codes generated). */
    private int imported;
    /** Rows rejected at confirm time (INVALID or DUPLICATE). */
    private int skipped;
    private int alreadyRegistered;
    private int alreadyAuthorized;
    /** One-time plaintext access codes for the imported rows; not persisted. */
    private List<ImportAccessCodeRow> codes;
    /** Rows that were not imported, with their reasons (for the error report). */
    private List<ImportRowPreview> errors;
}
