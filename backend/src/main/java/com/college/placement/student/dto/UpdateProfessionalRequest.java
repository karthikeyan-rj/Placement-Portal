package com.college.placement.student.dto;

import lombok.Data;

@Data
public class UpdateProfessionalRequest {
    private String skills;
    private String certifications;
    private String projects;
    private String resumeUrl;
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;
}
