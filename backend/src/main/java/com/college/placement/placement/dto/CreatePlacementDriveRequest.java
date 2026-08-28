package com.college.placement.placement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePlacementDriveRequest {

    @NotNull(message = "Company ID is required")
    private Long companyId;

    @NotBlank(message = "Job role is required")
    private String jobRole;

    private BigDecimal packageLpa;
    private String driveDate;
    private String registrationDeadline;
    private String location;
    private String jobDescription;
}
