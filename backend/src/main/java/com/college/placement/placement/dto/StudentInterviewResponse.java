package com.college.placement.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentInterviewResponse {
    private Long id;
    private Long studentProfileId;
    private String studentName;
    private String registerNumber;
    private Long placementDriveId;
    private String driveJobRole;
    private String companyName;
    private String roundName;
    private String status;
    private Boolean attended;
    private String remarks;
    private String interviewDate;
}
