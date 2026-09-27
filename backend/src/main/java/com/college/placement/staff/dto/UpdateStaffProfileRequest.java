package com.college.placement.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Self-service update payload for the caller's own staff profile.
 *
 * <p>There is deliberately no {@code userId} field: the owner is always derived
 * from the authenticated principal, so a caller cannot address another profile
 * by URL, query string or body injection.
 *
 * <p>Every field is optional and independent, so a caller can save just one
 * value. An absent field (omitted or JSON {@code null}) is left untouched, while
 * an empty or whitespace-only string clears the stored value.
 */
@Data
public class UpdateStaffProfileRequest {

    /** Optional. Accepts common human formats; not tied to one country format. */
    @Size(max = 25, message = "Phone must be at most 25 characters")
    @Pattern(regexp = "^\\s*$|^[0-9+()\\-.\\s]{6,25}$",
            message = "Phone may only contain digits, spaces and the symbols + - ( ) .")
    private String phone;

    /** Optional free text. Never derived from or overwritten by the account role. */
    @Size(max = 120, message = "Designation must be at most 120 characters")
    private String designation;

    @Size(max = 150, message = "Office location must be at most 150 characters")
    private String officeLocation;

    @Size(max = 1000, message = "Professional bio must be at most 1000 characters")
    private String bio;

    /** Optional. Must be an absolute http/https URL; other schemes are rejected. */
    @Size(max = 500, message = "LinkedIn URL must be at most 500 characters")
    @Pattern(regexp = "(?i)^\\s*$|^https?://\\S+$",
            message = "LinkedIn must be a valid http:// or https:// URL")
    private String linkedinUrl;

    @Size(max = 12, message = "You can list at most 12 areas of expertise")
    private List<@NotBlank(message = "Expertise entries cannot be blank")
            @Size(max = 60, message = "Each area of expertise must be at most 60 characters") String> expertise;
}
