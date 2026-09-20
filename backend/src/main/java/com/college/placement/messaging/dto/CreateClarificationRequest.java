package com.college.placement.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClarificationRequest {
    @NotBlank(message = "Clarification content is required")
    @Size(max = 1000, message = "Clarification content must be at most 1000 characters")
    private String content;
}