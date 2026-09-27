package com.college.placement.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCodeResponse {
    private String message;
    /** Short-lived, server-signed registration token */
    private String registrationToken;
}
