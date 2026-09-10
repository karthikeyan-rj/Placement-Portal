package com.college.placement.student.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStudentProfileRequest {
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date of birth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    @Size(max = 50, message = "Batch must be at most 50 characters")
    private String batch;

    @Size(max = 20, message = "Section must be at most 20 characters")
    private String section;

    private Boolean placementInterested;
}