package com.college.placement.user.dto;

import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    private String role;
    private Long departmentId;
}
