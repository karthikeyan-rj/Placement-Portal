package com.college.placement.accesscode;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Generates high-entropy 8-character access codes from a 32-symbol alphabet
 * (32^8 ~= 1.1e12 combinations). Extracted from the legacy file-based
 * AccessCodeService so every code-generating flow (legacy CSV generation and
 * the PO student import) uses the exact same generator — there is only ever
 * one code generator. Only the SHA-256 hash of a code is persisted; plaintext
 * codes are handed back to the caller exactly once and never logged.
 */
@Component
public class AccessCodeGenerator {

    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Returns a freshly generated code that is not already present in
     * {@code usedCodes}, adding it to the set. The caller keeps the set in
     * memory for the duration of one generation run so codes never repeat
     * within that run; uniqueness against the DB is guaranteed separately by
     * the SHA-256 hash comparison at registration time.
     */
    public String generateUnique(Set<String> usedCodes) {
        String code;
        do {
            code = generate();
        } while (!usedCodes.add(code));
        return code;
    }
}
