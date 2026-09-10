package com.college.placement.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private Long departmentId;
    private String departmentName;
    private Boolean active;
}
