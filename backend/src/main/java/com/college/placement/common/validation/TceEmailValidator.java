package com.college.placement.common.validation;

/**
 * Single source of truth for the TCE email-domain rule. Previously this logic
 * lived privately in AuthService; it is now shared with the student CSV import
 * so both registration and import validate emails identically.
 */
public final class TceEmailValidator {

    private static final String TCE_DOMAIN = "tce.edu";
    private static final String TCE_DOMAIN_SUFFIX = ".tce.edu";

    private TceEmailValidator() {
    }

    /**
     * Accepts the TCE email domain and any of its subdomains, e.g.
     * name@tce.edu, name@student.tce.edu, name@staff.tce.edu.
     * Rejects lookalike domains such as name@tce.edu.fake.com.
     */
    public static boolean isAllowedTceEmail(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) return false;
        String local = email.substring(0, at);
        if (local.trim().isEmpty()) return false;
        String domain = email.substring(at + 1).toLowerCase();
        return TCE_DOMAIN.equals(domain) || domain.endsWith(TCE_DOMAIN_SUFFIX);
    }
}
