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
public class ImportConfirmRequest {
    /** The READY rows from the preview step; re-validated server-side before import. */
    private List<ImportRowRequest> rows;
}
