package com.college.placement.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentAggregateResponse {
    private Long departmentId;
    private String departmentName;
    private boolean active;
    private Integer prLimit;
    private long studentCount;
    private long pcCount;
    private long prCount;
}