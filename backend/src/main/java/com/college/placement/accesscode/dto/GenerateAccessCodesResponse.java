package com.college.placement.accesscode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAccessCodesResponse {
    private int totalCsvRows;
    private int created;
    private int skipped;
    private String plaintextFile;
}
