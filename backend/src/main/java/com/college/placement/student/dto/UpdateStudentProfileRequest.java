package com.college.placement.student.dto;

import lombok.Data;

@Data
public class UpdateStudentProfileRequest {
    private String phone;
    private String dateOfBirth;
    private String batch;
    private String section;
}
