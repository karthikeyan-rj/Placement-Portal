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
public class ImportPreviewResponse {
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int duplicateRows;
    private int alreadyRegistered;
    private int alreadyAuthorized;
    private List<ImportRowPreview> rows;
}
