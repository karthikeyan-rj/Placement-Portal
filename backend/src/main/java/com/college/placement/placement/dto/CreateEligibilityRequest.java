package com.college.placement.placement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateEligibilityRequest {
    private BigDecimal minCgpa;
    private Integer maxActiveBacklogs;
    private BigDecimal minTenthPct;
    private BigDecimal minTwelfthPct;
    private BigDecimal minDiplomaPct;
    private List<Long> allowedDepartmentIds;
}
