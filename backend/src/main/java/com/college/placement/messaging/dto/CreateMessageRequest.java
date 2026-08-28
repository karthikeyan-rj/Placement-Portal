package com.college.placement.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class CreateMessageRequest {
    @NotBlank(message = "Title is required")
    private String title;
    @NotBlank(message = "Content is required")
    private String content;
    private String messageType;
    private List<Long> recipientIds;
    private Long departmentId;
    private String targetRole;
}
