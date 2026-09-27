package com.college.placement.profile;

import com.college.placement.common.enums.Role;
import com.college.placement.profile.dto.ProfileResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.staff.StaffProfileService;
import com.college.placement.staff.dto.StaffProfileResponse;
import com.college.placement.student.StudentService;
import com.college.placement.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final SecurityUtils securityUtils;
    private final StudentService studentService;
    private final StaffProfileService staffProfileService;

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile() {
        User user = securityUtils.getCurrentUser();

        ProfileResponse.ProfileResponseBuilder builder = ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.getActive())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null);

        if (user.getRole() == Role.STUDENT || user.getRole() == Role.PR) {
            try {
                builder.studentProfile(studentService.getMyStudentProfileOrNull());
            } catch (Exception e) {
                log.warn("Student profile inconsistency for user {} ({}): {}",
                        user.getId(), user.getRole(), e.getMessage());
                builder.studentProfile(null);
            }
        } else if (StaffProfileService.isStaffRole(user.getRole())) {
            try {
                builder.staffProfile(staffProfileService.getMyStaffProfileOrNull());
            } catch (Exception e) {
                log.warn("Staff profile inconsistency for user {} ({}): {}",
                        user.getId(), user.getRole(), e.getMessage());
                builder.staffProfile(StaffProfileResponse.builder().build());
            }
        }
        return builder.build();
    }
}