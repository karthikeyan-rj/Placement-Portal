package com.college.placement.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String description;
    private String companyType;
    private String website;
}
