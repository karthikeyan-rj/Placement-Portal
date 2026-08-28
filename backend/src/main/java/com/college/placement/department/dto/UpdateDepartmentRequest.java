package com.college.placement.department.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDepartmentRequest {

    @Size(min = 2, max = 100, message = "Department name must be between 2 and 100 characters")
    private String name;

    private Boolean active;
}
