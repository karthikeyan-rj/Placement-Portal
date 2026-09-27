package com.college.placement.mockinterview.service;

import com.college.placement.common.enums.MockInterviewType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for which preparation modules each interview mode draws
 * from.
 *
 * <p>The frontend never hardcodes module names: it renders whatever
 * {@code /api/mock-interviews/options} returns, which is derived from the active
 * modules in the database. This catalog only decides the grouping, and any code
 * it names that is not actually present in the preparation domain is simply
 * skipped at resolution time.
 */
@Component
public class MockInterviewModeCatalog {

    /** Modules that represent technical / problem-solving interview content. */
    private static final Set<String> TECHNICAL_CODES = Set.of("DSA", "DBMS", "OS", "CN", "JAVA_OOP", "SQL", "APTITUDE");

    /** Situational / behavioural module. */
    private static final Set<String> HR_CODES = Set.of("HR");

    /**
     * Project-defence prompts. These are open discussion questions about a
     * student's own projects, so they are offered in the mixed session rather
     * than a purely technical one.
     */
    private static final Set<String> PROJECT_CODES = Set.of("PROJECT");

    public record ModeInfo(MockInterviewType type, String label, String description) {
    }

    private static final Map<MockInterviewType, ModeInfo> MODES = Map.of(
            MockInterviewType.TECHNICAL, new ModeInfo(
                    MockInterviewType.TECHNICAL,
                    "Technical Interview",
                    "Core placement subjects: data structures, databases, operating systems, " +
                            "networks, Java, SQL and aptitude."),
            MockInterviewType.HR_BEHAVIORAL, new ModeInfo(
                    MockInterviewType.HR_BEHAVIORAL,
                    "HR / Behavioral Interview",
                    "Behavioural and situational questions from the HR preparation module."),
            MockInterviewType.MIXED, new ModeInfo(
                    MockInterviewType.MIXED,
                    "Mixed Interview",
                    "A blend of technical, aptitude, HR and project-defence questions.")
    );

    public Map<MockInterviewType, ModeInfo> allModes() {
        return MODES;
    }

    public ModeInfo info(MockInterviewType type) {
        return MODES.get(type);
    }

    public String label(MockInterviewType type) {
        return MODES.get(type).label();
    }

    /**
     * Module codes the given mode draws from. {@link MockInterviewType#MIXED}
     * spans every known group.
     */
    public Set<String> moduleCodesFor(MockInterviewType type) {
        return switch (type) {
            case TECHNICAL -> new LinkedHashSet<>(TECHNICAL_CODES);
            case HR_BEHAVIORAL -> new LinkedHashSet<>(HR_CODES);
            case MIXED -> {
                Set<String> all = new LinkedHashSet<>(TECHNICAL_CODES);
                all.addAll(HR_CODES);
                all.addAll(PROJECT_CODES);
                yield all;
            }
        };
    }
}
