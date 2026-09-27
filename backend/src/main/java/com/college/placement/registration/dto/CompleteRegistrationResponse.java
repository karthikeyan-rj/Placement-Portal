package com.college.placement.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteRegistrationResponse {
    private String message;
    private Long userId;
    private String name;
    private String email;
    private String role;
}
