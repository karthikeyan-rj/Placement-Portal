package com.college.placement.placement.dto;

import com.college.placement.common.enums.PlacementDriveStatus;
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

    /**
     * Initial status. Defaults to UPCOMING. Creating directly as REGISTRATION_OPEN triggers
     * the same single notification as a later transition into that status.
     */
    private PlacementDriveStatus status;
}
