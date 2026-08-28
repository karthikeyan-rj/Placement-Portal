package com.college.placement.contact.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateContactRequestStatus {
    @NotBlank(message = "Status is required")
    private String status;
}
