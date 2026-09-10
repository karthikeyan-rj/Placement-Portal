package com.college.placement.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSummaryResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String registerNumber;
    private Long departmentId;
    private String departmentName;
    private BigDecimal cgpa;
    private Boolean placementInterested;
    private String placementStatus;
}