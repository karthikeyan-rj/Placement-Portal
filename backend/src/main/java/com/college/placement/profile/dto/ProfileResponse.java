package com.college.placement.profile.dto;

import com.college.placement.student.dto.StudentProfileResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private Boolean active;
    private Long departmentId;
    private String departmentName;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private StudentProfileResponse studentProfile;
}