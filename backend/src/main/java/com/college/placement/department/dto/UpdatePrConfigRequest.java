package com.college.placement.department.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdatePrConfigRequest {

    @Min(value = 1, message = "Maximum PRs must be at least 1")
    private Integer maxPrs;
}
