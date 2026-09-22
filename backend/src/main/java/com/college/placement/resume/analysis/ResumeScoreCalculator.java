package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.List;

import static com.college.placement.resume.analysis.ScoreWeights.*;

/**
 * Computes the five transparent category scores, the overall Resume Readiness
 * and the ATS Compatibility Estimate. Every rule is documented in the constants
 * and comments below; the result is fully deterministic (no randomness).
 */
@Component
public class ResumeScoreCalculator {

    public void compute(AnalysisOutcome outcome) {
        int profile = profileScore(outcome);
        int content = contentScore(outcome);
        int impact = impactScore(outcome);
        int formatting = formattingScore(outcome);
        int links = linksScore(outcome);

        double readiness = PROFILE_WEIGHT * profile
                + CONTENT_WEIGHT * content
                + IMPACT_WEIGHT * impact
                + FORMATTING_WEIGHT * formatting
                + LINKS_WEIGHT * links;

        double ats = ATS_FORMATTING_FACTOR * formatting
                + ATS_PROFILE_FACTOR * profile
                + ATS_LINKS_FACTOR * links;

        outcome.setCategoryScores(profile, content, impact, formatting, links,
                clamp(Math.round((float) readiness)), clamp(Math.round((float) ats)));
    }

    /**
     * Profile Completeness (weight 25%): heavily weighted on the core student
     * sections Education + Skills + Projects (60 points). Summary, Experience,
     * Certifications, Achievements, POR and Publications earn bonus points
     * (capped at 40) so a missing Experience section does NOT sink a fresher.
     */
    private int profileScore(AnalysisOutcome outcome) {
        boolean education = found(outcome, ResumeSection.EDUCATION);
        boolean skills = found(outcome, ResumeSection.SKILLS);
        boolean projects = found(outcome, ResumeSection.PROJECTS);

        int coreFound = (education ? 1 : 0) + (skills ? 1 : 0) + (projects ? 1 : 0);
        int score = (int) Math.round(60.0 * coreFound / 3.0);

        int bonus = 0;
        if (found(outcome, ResumeSection.SUMMARY)) bonus += 8;
        if (found(outcome, ResumeSection.EXPERIENCE)) bonus += 8;
        else if (projects && (education || skills)) bonus += 4; // fresher credit
        if (found(outcome, ResumeSection.CERTIFICATIONS)) bonus += 8;
        if (found(outcome, ResumeSection.ACHIEVEMENTS)) bonus += 5;
        if (found(outcome, ResumeSection.POR)) bonus += 4;
        if (found(outcome, ResumeSection.PUBLICATIONS)) bonus += 3;

        return clamp(score + Math.min(bonus, 40));
    }

    /**
     * Content Quality (weight 30%): rewards substantial text, a real Projects
     * section with action-leading bullets and technology mentions, plus
     * Experience/Certifications/Achievements. First-person filler, vague bullets
     * and oversized bullets reduce the score.
     */
    private int contentScore(AnalysisOutcome outcome) {
        if (!outcome.isTextExtractable()) {
            return 10;
        }
        int words = outcome.getWordCount();
        int base;
        if (words >= 150) base = 45;
        else if (words >= 90) base = 35;
        else if (words >= 30) base = 25;
        else base = 15;

        List<ProjectBullet> bullets = outcome.getBullets();
        long actionBullets = bullets.stream().filter(ProjectBullet::startsWithActionVerb).count();
        long techBullets = bullets.stream().filter(ProjectBullet::mentionsTechnology).count();

        int score = base;
        if (found(outcome, ResumeSection.PROJECTS)) score += 12;
        if (!bullets.isEmpty()) score += 8;
        if (!bullets.isEmpty() && actionBullets * 2 >= bullets.size()) score += 10;
        else if (actionBullets > 0) score += 5;
        if (techBullets > 0) score += 8;

        if (found(outcome, ResumeSection.EXPERIENCE)) score += 5;
        if (found(outcome, ResumeSection.CERTIFICATIONS)) score += 5;
        if (found(outcome, ResumeSection.ACHIEVEMENTS)) score += 3;

        long firstPerson = bullets.stream().filter(ProjectBullet::firstPerson).count();
        score -= (int) Math.min(12, firstPerson * 6);
        long vague = bullets.stream().filter(ProjectBullet::vague).count();
        if (vague >= 2) score -= 6;
        long tooLong = bullets.stream().filter(ProjectBullet::tooLong).count();
        if (tooLong >= 2) score -= 4;

        return clamp(score);
    }

    /**
     * Impact (weight 20%): 70% measurable outcomes among project/experience
     * bullets, 30% action verbs. Absence of bullets yields a low but non-zero
     * score; the analyzer recommends adding measurable impact rather than
     * inventing metrics.
     */
    private int impactScore(AnalysisOutcome outcome) {
        List<ProjectBullet> bullets = outcome.getBullets();
        if (!outcome.isTextExtractable() || bullets.isEmpty()) {
            return 10;
        }
        long measurable = bullets.stream().filter(ProjectBullet::measurable).count();
        long action = bullets.stream().filter(ProjectBullet::startsWithActionVerb).count();
        double m = (double) measurable / bullets.size();
        double a = (double) action / bullets.size();
        int score = (int) Math.round(a * 30 + m * 70);
        return clamp(Math.max(score, 12));
    }

    /**
     * Formatting / parsing (weight 15%): image-only PDFs floor at 15. Page
     * count (1 page = 34), standard headings (+30), unusual character ratio and
     * text density make up the rest.
     */
    private int formattingScore(AnalysisOutcome outcome) {
        if (!outcome.isTextExtractable()) {
            return 15;
        }
        int score = switch (outcome.getPageCount()) {
            case 1 -> 34;
            case 2 -> 28;
            case 3 -> 16;
            default -> 9;
        };
        score += outcome.isStandardHeadings() ? 30 : 12;

        double special = outcome.getSpecialCharacterRatio();
        if (special <= 0.03) score += 21;
        else if (special <= 0.08) score += 13;
        else if (special <= 0.15) score += 7;
        else score += 3;

        score += outcome.getWordCount() >= 100 ? 15 : 4;

        return clamp(score);
    }

    /**
     * Professional Links (weight 10%): proportional credit for email, phone,
     * LinkedIn, GitHub and portfolio. A resume with none of these scores 0.
     */
    private int linksScore(AnalysisOutcome outcome) {
        ContactPresence contact = outcome.getContact();
        int weight = 0;
        if (contact.isEmailPresent()) weight += LINK_EMAIL;
        if (contact.isPhonePresent()) weight += LINK_PHONE;
        if (contact.isLinkedinPresent()) weight += LINK_LINKEDIN;
        if (contact.isGithubPresent()) weight += LINK_GITHUB;
        if (contact.isPortfolioPresent()) weight += LINK_PORTFOLIO;
        return clamp(weight);
    }

    private boolean found(AnalysisOutcome outcome, ResumeSection section) {
        return outcome.getSections().stream()
                .anyMatch(s -> s.getName().equals(section.getLabel()) && s.isFound());
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}