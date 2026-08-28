package com.college.placement.placement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInterviewRequest {
    @NotNull(message = "Student profile ID is required")
    private Long studentProfileId;
    @NotNull(message = "Placement drive ID is required")
    private Long placementDriveId;
    @NotBlank(message = "Round name is required")
    private String roundName;
    private String status;
    private Boolean attended;
    private String remarks;
    private String interviewDate;
}
