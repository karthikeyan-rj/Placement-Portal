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
public class EligibilityCriteriaResponse {
    private Long id;
    private BigDecimal minCgpa;
    private Integer maxActiveBacklogs;
    private BigDecimal minTenthPct;
    private BigDecimal minTwelfthPct;
    private BigDecimal minDiplomaPct;
    private List<Long> allowedDepartmentIds;
    private List<String> allowedDepartmentNames;
}
