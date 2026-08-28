package com.college.placement.company.dto;

import com.college.placement.common.enums.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {
    private Long id;
    private String name;
    private String description;
    private String companyType;
    private String website;
    private boolean active;
}
