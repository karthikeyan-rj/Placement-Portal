package com.college.placement.student.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfessionalRequest {
    @Size(max = 10000, message = "Skills must be at most 10000 characters")
    private String skills;

    @Size(max = 10000, message = "Certifications must be at most 10000 characters")
    private String certifications;

    @Size(max = 10000, message = "Projects must be at most 10000 characters")
    private String projects;

    @Size(max = 500, message = "Resume URL must be at most 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Resume URL must start with http:// or https://")
    private String resumeUrl;

    @Size(max = 500, message = "GitHub URL must be at most 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "GitHub URL must start with http:// or https://")
    private String githubUrl;

    @Size(max = 500, message = "LinkedIn URL must be at most 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "LinkedIn URL must start with http:// or https://")
    private String linkedinUrl;

    @Size(max = 500, message = "Portfolio URL must be at most 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Portfolio URL must start with http:// or https://")
    private String portfolioUrl;
}