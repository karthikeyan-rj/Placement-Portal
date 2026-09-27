package com.college.placement.registration;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RegistrationCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure 6-digit numeric verification code (000000 - 999999).
     */
    public String generate6DigitCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }
}
