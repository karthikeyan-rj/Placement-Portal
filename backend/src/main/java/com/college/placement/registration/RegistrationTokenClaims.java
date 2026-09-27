package com.college.placement.registration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationTokenClaims {
    private Long authorizedStudentId;
    private String email;
    private String registerNumber;
    private Long verificationCodeId;
}
