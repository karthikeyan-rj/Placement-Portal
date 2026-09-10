package com.college.placement.student.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateAcademicRequest {
    @DecimalMin(value = "0.0", message = "10th percentage must be at least 0")
    @DecimalMax(value = "100.0", message = "10th percentage must be at most 100")
    private BigDecimal tenthPercentage;

    @DecimalMin(value = "0.0", message = "12th percentage must be at least 0")
    @DecimalMax(value = "100.0", message = "12th percentage must be at most 100")
    private BigDecimal twelfthPercentage;

    @DecimalMin(value = "0.0", message = "Diploma percentage must be at least 0")
    @DecimalMax(value = "100.0", message = "Diploma percentage must be at most 100")
    private BigDecimal diplomaPercentage;

    @DecimalMin(value = "0.0", message = "CGPA must be at least 0")
    @DecimalMax(value = "10.0", message = "CGPA must be at most 10")
    private BigDecimal cgpa;

    @Min(value = 0, message = "Active backlogs cannot be negative")
    private Integer activeBacklogs;

    @Min(value = 0, message = "History of backlogs cannot be negative")
    private Integer historyOfBacklogs;
}