package com.college.placement.profile;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.profile.dto.ProfileResponse;
import com.college.placement.staff.StaffProfileService;
import com.college.placement.staff.dto.StaffProfileResponse;
import com.college.placement.staff.dto.UpdateStaffProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final StaffProfileService staffProfileService;

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile() {
        return ApiResponse.success("Profile retrieved successfully.", profileService.getMyProfile());
    }

    /**
     * Self-service update of the caller's own staff (PC / PO) profile.
     *
     * <p>The owner is taken from the authenticated principal only - there is no
     * userId in the path, query string or body, so one staff member can never
     * edit another's profile. STUDENT and PR callers are rejected with 403
     * because they own a StudentProfile instead.
     */
    @PutMapping("/me/staff")
    public ApiResponse<StaffProfileResponse> updateMyStaffProfile(
            @Valid @RequestBody UpdateStaffProfileRequest request) {
        return ApiResponse.success("Staff profile updated successfully.",
                staffProfileService.updateMyStaffProfile(request));
    }
}
