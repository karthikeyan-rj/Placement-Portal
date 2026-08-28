package com.college.placement.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String registerNumber;
    private String phone;
    private String dateOfBirth;
    private Long departmentId;
    private String departmentName;
    private String batch;
    private String section;

    private BigDecimal tenthPercentage;
    private BigDecimal twelfthPercentage;
    private BigDecimal diplomaPercentage;
    private BigDecimal cgpa;
    private Integer activeBacklogs;
    private Integer historyOfBacklogs;

    private String skills;
    private String certifications;
    private String projects;
    private String resumeUrl;
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;

    private Boolean placementInterested;
    private String placementStatus;
    private Integer interviewsAttended;
}
