package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Detects whether professional contact signals are present in the extracted
 * text. Only boolean presence is ever reported; the actual email/phone
 * values are neither returned nor stored.
 */
@Component
public class ContactInspector {

    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?:(?:\\+\\d{1,3}[\\s-]?)?(?:\\(\\d{2,3}\\)|\\d{2,4})[\\s.-]?\\d{3,4}[\\s.-]?\\d{3,4}|\\b\\d{10}\\b)");
    private static final Pattern LINKEDIN = Pattern.compile(
            "(?i)linkedin\\.com[/\\s]*in[/\\s]*[a-zA-Z0-9-]+");
    private static final Pattern GITHUB = Pattern.compile(
            "(?i)github\\.com[/\\s]*[a-zA-Z0-9-]+");
    private static final Pattern PORTFOLIO = Pattern.compile(
            "(?i)\\b(portfolio|personal website|personal site|sites\\.google|github\\.io)\\b");

    public ContactPresence inspect(String text) {
        boolean emailPresent = EMAIL.matcher(text).find();
        boolean phonePresent = PHONE.matcher(text).find();
        boolean linkedinPresent = LINKEDIN.matcher(text).find();
        boolean githubPresent = GITHUB.matcher(text).find();
        boolean portfolioPresent = PORTFOLIO.matcher(text).find();
        return ContactPresence.builder()
                .emailPresent(emailPresent)
                .phonePresent(phonePresent)
                .linkedinPresent(linkedinPresent)
                .githubPresent(githubPresent)
                .portfolioPresent(portfolioPresent)
                .build();
    }
}