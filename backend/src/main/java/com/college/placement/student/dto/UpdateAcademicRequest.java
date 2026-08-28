package com.college.placement.student.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateAcademicRequest {
    private BigDecimal tenthPercentage;
    private BigDecimal twelfthPercentage;
    private BigDecimal diplomaPercentage;
    private BigDecimal cgpa;
    private Integer activeBacklogs;
    private Integer historyOfBacklogs;
}
