package com.college.placement.staff;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.staff.dto.StaffProfileResponse;
import com.college.placement.staff.dto.UpdateStaffProfileRequest;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffProfileService {

    /** The only roles that own a StaffProfile. PR is deliberately excluded. */
    private static final Set<Role> STAFF_ROLES = Set.of(Role.PC, Role.PO);

    private final StaffProfileRepository staffProfileRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** True when the role owns a StaffProfile rather than a StudentProfile. */
    public static boolean isStaffRole(Role role) {
        return STAFF_ROLES.contains(role);
    }

    /**
     * Read-only, never creates a row. A staff member who has never saved
     * anything gets an all-null section instead of an error, so an empty
     * optional profile can never break the profile page.
     */
    @Transactional(readOnly = true)
    public StaffProfileResponse getMyStaffProfileOrNull() {
        User user = securityUtils.getCurrentUser();
        if (!isStaffRole(user.getRole())) {
            return null;
        }
        return staffProfileRepository.findByUserId(user.getId())
                .map(StaffProfileService::toResponse)
                .orElseGet(() -> StaffProfileResponse.builder().build());
    }

    /**
     * Saves the caller's own staff profile, creating the row on first save.
     *
     * <p>The owner always comes from the authenticated principal, so this method
     * cannot be pointed at another user. Fields left out of the request are not
     * touched; fields sent as blank are cleared.
     */
    @Transactional
    public StaffProfileResponse updateMyStaffProfile(UpdateStaffProfileRequest request) {
        User user = securityUtils.getCurrentUser();
        securityUtils.requireAnyRole(Role.PC, Role.PO);

        StaffProfile profile = staffProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> staffProfileRepository.save(
                        StaffProfile.builder().user(user).build()));

        Set<String> changed = new LinkedHashSet<>();

        applyIfPresent(request.getPhone(), UnaryOperator.identity(), profile::setPhone, changed, "phone");
        applyIfPresent(request.getDesignation(), UnaryOperator.identity(), profile::setDesignation,
                changed, "designation");
        applyIfPresent(request.getOfficeLocation(), UnaryOperator.identity(), profile::setOfficeLocation,
                changed, "officeLocation");
        applyIfPresent(request.getBio(), UnaryOperator.identity(), profile::setBio, changed, "bio");
        applyIfPresent(request.getLinkedinUrl(), this::validateUrl,
                profile::setLinkedinUrl, changed, "linkedinUrl");

        if (request.getExpertise() != null) {
            profile.setExpertise(normalizeExpertise(request.getExpertise()));
            changed.add("expertise");
        }

        staffProfileRepository.saveAndFlush(profile);

        if (!changed.isEmpty()) {
            // Only field NAMES are recorded - never the phone, bio, URL or
            // expertise text itself.
            auditService.log("STAFF_PROFILE_UPDATED", "StaffProfile", profile.getId(),
                    String.join(",", changed));
        }
        return toResponse(profile);
    }

    /**
     * Applies a trimmed value, or clears the field when the caller sent blank.
     * A {@code null} value means "not supplied" and is left untouched, which is
     * what makes partial saves work.
     *
     * <p>{@code normalize} runs only on a real value, never on the blank used to
     * clear a field - so clearing a URL does not try to parse an empty string.
     */
    private void applyIfPresent(String raw, UnaryOperator<String> normalize,
                                java.util.function.Consumer<String> setter,
                                Set<String> changed, String field) {
        if (raw == null) {
            return;
        }
        String value = raw.trim();
        setter.accept(value.isEmpty() ? null : normalize.apply(value));
        changed.add(field);
    }

    /** Trims, drops blanks, removes case-insensitive duplicates, keeps order. */
    private List<String> normalizeExpertise(List<String> raw) {
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String value = item.trim();
            if (value.isEmpty()) {
                continue;
            }
            String key = value.toLowerCase();
            if (unique.stream().noneMatch(existing -> existing.toLowerCase().equals(key))) {
                unique.add(value);
            }
        }
        return unique.isEmpty() ? null : new ArrayList<>(unique);
    }

    /**
     * Belt-and-braces URL check on top of the bean-validation pattern: only
     * absolute http/https URLs with a host are accepted, so {@code javascript:}
     * and other script-bearing schemes can never be stored.
     */
    private String validateUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("LinkedIn must be a valid http:// or https:// URL.");
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("LinkedIn must be a valid http:// or https:// URL.");
            }
            String lower = scheme.toLowerCase();
            if (!lower.equals("http") && !lower.equals("https")) {
                throw new BadRequestException("LinkedIn must be a valid http:// or https:// URL.");
            }
            return value;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("LinkedIn must be a valid http:// or https:// URL.");
        }
    }

    private static StaffProfileResponse toResponse(StaffProfile profile) {
        return StaffProfileResponse.builder()
                .phone(profile.getPhone())
                .designation(profile.getDesignation())
                .officeLocation(profile.getOfficeLocation())
                .bio(profile.getBio())
                .linkedinUrl(profile.getLinkedinUrl())
                .expertise(profile.getExpertise() == null || profile.getExpertise().isEmpty()
                        ? null : List.copyOf(profile.getExpertise()))
                .build();
    }
}
