package com.college.placement.accesscode;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes access codes with SHA-256. Access codes are high-entropy random
 * 8-character tokens (32^8 ~= 1.1e12 combinations), so a fast hash is
 * cryptographically appropriate. Only the hash is ever persisted.
 */
@Component
public class AccessCodeHasher {

    private static final MessageDigest DIGEST = createDigest();

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String hash(String code) {
        byte[] digest = DIGEST.digest(code.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    public boolean matches(String code, String hash) {
        return hash != null && hash.equalsIgnoreCase(hash(code));
    }
}
