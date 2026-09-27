package com.college.placement.registration;

import com.college.placement.common.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class RegistrationTokenService {

    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_REGISTRATION = "REGISTRATION";
    public static final String CLAIM_AUTHORIZED_STUDENT_ID = "authorizedStudentId";
    public static final String CLAIM_REGISTER_NUMBER = "registerNumber";
    public static final String CLAIM_VERIFICATION_CODE_ID = "verificationCodeId";

    /** 10 minutes in milliseconds */
    private static final long REGISTRATION_TOKEN_EXPIRY_MS = 10 * 60 * 1000L;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a short-lived (10 min), cryptographically signed registration token.
     * Contains identity bindings and purpose=REGISTRATION so it cannot be used as a login token.
     */
    public String generateRegistrationToken(Long authorizedStudentId, String email,
                                           String registerNumber, Long verificationCodeId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REGISTRATION_TOKEN_EXPIRY_MS);

        return Jwts.builder()
                .subject(email)
                .claim(PURPOSE_CLAIM, PURPOSE_REGISTRATION)
                .claim(CLAIM_AUTHORIZED_STUDENT_ID, authorizedStudentId)
                .claim(CLAIM_REGISTER_NUMBER, registerNumber)
                .claim(CLAIM_VERIFICATION_CODE_ID, verificationCodeId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates that the token is untampered, unexpired, and has purpose=REGISTRATION.
     * Returns the verified claims or throws BadRequestException.
     */
    public RegistrationTokenClaims validateRegistrationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Registration token is required.");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String purpose = claims.get(PURPOSE_CLAIM, String.class);
            if (!PURPOSE_REGISTRATION.equals(purpose)) {
                throw new BadRequestException("Invalid token purpose.");
            }

            Long authorizedStudentId = claims.get(CLAIM_AUTHORIZED_STUDENT_ID, Long.class);
            if (authorizedStudentId == null) {
                // handle integer vs long conversion from JSON claims
                Number num = (Number) claims.get(CLAIM_AUTHORIZED_STUDENT_ID);
                if (num != null) authorizedStudentId = num.longValue();
            }

            Long verificationCodeId = claims.get(CLAIM_VERIFICATION_CODE_ID, Long.class);
            if (verificationCodeId == null) {
                Number num = (Number) claims.get(CLAIM_VERIFICATION_CODE_ID);
                if (num != null) verificationCodeId = num.longValue();
            }

            String email = claims.getSubject();
            String registerNumber = claims.get(CLAIM_REGISTER_NUMBER, String.class);

            if (authorizedStudentId == null || email == null || registerNumber == null) {
                throw new BadRequestException("Registration token is missing required identity claims.");
            }

            return RegistrationTokenClaims.builder()
                    .authorizedStudentId(authorizedStudentId)
                    .email(email)
                    .registerNumber(registerNumber)
                    .verificationCodeId(verificationCodeId)
                    .build();

        } catch (JwtException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid or expired registration token. Please verify your email again.");
        }
    }
}
