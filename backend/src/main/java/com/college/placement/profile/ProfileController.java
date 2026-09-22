package com.college.placement.profile;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.profile.dto.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile() {
        return ApiResponse.success("Profile retrieved successfully.", profileService.getMyProfile());
    }
}