package com.college.placement.security;

import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.UnauthorizedException;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserPrincipal appUserPrincipal) {
            return appUserPrincipal.getUser();
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public Role getCurrentUserRole() {
        return getCurrentUser().getRole();
    }

    public boolean hasRole(Role role) {
        return getCurrentUserRole() == role;
    }

    public boolean isPO() {
        return hasRole(Role.PO);
    }

    public boolean isPC() {
        return hasRole(Role.PC);
    }

    public boolean isPR() {
        return hasRole(Role.PR);
    }

    public boolean isStudent() {
        return hasRole(Role.STUDENT);
    }

    public void requireRole(Role role) {
        if (!hasRole(role)) {
            throw new ForbiddenException("This action requires the " + role + " role.");
        }
    }

    public void requireAnyRole(Role... roles) {
        Role currentRole = getCurrentUserRole();
        for (Role role : roles) {
            if (currentRole == role) return;
        }
        throw new ForbiddenException("You do not have the required role to perform this action.");
    }
}
