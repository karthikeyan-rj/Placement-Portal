package com.college.placement.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestResponse {
    private Long id;
    private Long studentProfileId;
    private String studentName;
    private String registerNumber;
    private String departmentName;
    private Long targetUserId;
    private String targetUserName;
    private String subject;
    private String message;
    private String status;
    private String createdAt;
    private String resolvedAt;
}
