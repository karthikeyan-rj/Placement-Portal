package com.college.placement.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestCodeResponse {
    private String message;
    /** Masked email shown to the user, e.g. k***********@student.tce.edu */
    private String maskedEmail;
}
