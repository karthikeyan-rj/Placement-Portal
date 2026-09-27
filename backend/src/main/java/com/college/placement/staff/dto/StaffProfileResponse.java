package com.college.placement.staff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Safe, entity-free view of a staff member's editable professional profile.
 * Contains no role, department, name, email or account status - those stay on
 * {@code User}/{@code Department} and are surfaced by the profile envelope.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class StaffProfileResponse {
    private String phone;
    private String designation;
    private String officeLocation;
    private String bio;
    private String linkedinUrl;
    private List<String> expertise;
}
