package com.college.placement.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementDriveResponse {
    private Long id;
    private Long companyId;
    private String companyName;
    private String companyType;
    private String jobRole;
    private BigDecimal packageLpa;
    private String driveDate;
    private String registrationDeadline;
    private String location;
    private String jobDescription;
    private String status;
    private EligibilityCriteriaResponse eligibilityCriteria;
}
