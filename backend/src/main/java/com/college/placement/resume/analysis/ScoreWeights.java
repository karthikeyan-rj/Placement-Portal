package com.college.placement.resume.analysis;

/**
 * Documented scoring weights for the Resume Analyzer. Keep the numbers in one
 * place so every score is explainable and no magic constants leak into logic.
 */
public final class ScoreWeights {

    private ScoreWeights() {
    }

    /** Readiness = weighted blend of the five categories (0-100). */
    public static final double PROFILE_WEIGHT = 0.25;
    public static final double CONTENT_WEIGHT = 0.30;
    public static final double IMPACT_WEIGHT = 0.20;
    public static final double FORMATTING_WEIGHT = 0.15;
    public static final double LINKS_WEIGHT = 0.10;

    /**
     * ATS Compatibility Estimate = explicit heuristic blend. This is a
     * formatting/contact estimate, never a claim about any employer's system.
     */
    public static final double ATS_FORMATTING_FACTOR = 0.50;
    public static final double ATS_PROFILE_FACTOR = 0.25;
    public static final double ATS_LINKS_FACTOR = 0.25;

    /** Contact signal weights for the Professional Links category (sum 100). */
    public static final int LINK_EMAIL = 25;
    public static final int LINK_PHONE = 20;
    public static final int LINK_LINKEDIN = 25;
    public static final int LINK_GITHUB = 20;
    public static final int LINK_PORTFOLIO = 10;

    /** Maximum number of recommendations surfaced to the user. */
    public static final int MAX_RECOMMENDATIONS = 8;
}