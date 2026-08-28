package com.college.placement.contact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateContactRequest {
    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
    @NotBlank(message = "Subject is required")
    private String subject;
    @NotBlank(message = "Message is required")
    private String message;
}
