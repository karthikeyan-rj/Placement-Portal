package com.college.placement.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private String userEmail;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
    private String createdAt;
}
