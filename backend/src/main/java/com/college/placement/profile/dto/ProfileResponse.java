package com.college.placement.profile.dto;

import com.college.placement.staff.dto.StaffProfileResponse;
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

    /** Populated for STUDENT and PR only; always null for PC and PO. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private StudentProfileResponse studentProfile;

    /**
     * Populated for PC and PO only; always null for STUDENT and PR. An
     * all-null section means the staff member has not saved anything yet.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private StaffProfileResponse staffProfile;
}