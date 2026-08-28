package com.college.placement.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStudentRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Register number is required")
    private String registerNumber;

    private String phone;
    private String dateOfBirth;
    private String batch;
    private String section;
}
