package com.college.placement.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageReactionRequest {
    @NotBlank(message = "Reaction type is required")
    private String reaction;
}
