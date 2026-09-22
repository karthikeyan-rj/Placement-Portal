package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.college.placement.resume.analysis.ScoreWeights.MAX_RECOMMENDATIONS;

/**
 * Builds the prioritised improvement list. Rules are advisory only and never
 * fabricate achievements, skills, experience or metrics. HIGH > MEDIUM > LOW,
 * capped at {@link ScoreWeights#MAX_RECOMMENDATIONS} entries.
 */
@Component
public class RecommendationBuilder {

    public List<RecommendationEntry> build(AnalysisOutcome outcome) {
        List<RecommendationEntry> candidates = new ArrayList<>();

        boolean projectsFound = found(outcome, ResumeSection.PROJECTS);
        boolean skillsFound = found(outcome, ResumeSection.SKILLS);
        boolean experienceFound = found(outcome, ResumeSection.EXPERIENCE);
        boolean certificationsFound = found(outcome, ResumeSection.CERTIFICATIONS);
        boolean achievementsFound = found(outcome, ResumeSection.ACHIEVEMENTS);
        boolean summaryFound = found(outcome, ResumeSection.SUMMARY);
        boolean porFound = found(outcome, ResumeSection.POR);

        List<ProjectBullet> bullets = outcome.getBullets();
        long measurable = bullets.stream().filter(ProjectBullet::measurable).count();
        long action = bullets.stream().filter(ProjectBullet::startsWithActionVerb).count();
        long firstPerson = bullets.stream().filter(ProjectBullet::firstPerson).count();
        long tooLong = bullets.stream().filter(ProjectBullet::tooLong).count();

        ContactPresence contact = outcome.getContact();

        // ---------------- HIGH ----------------
        if (!outcome.isTextExtractable()) {
            candidates.add(entry("HIGH", "This PDF appears to be scanned or image-based, so text analysis "
                    + "is limited. Export and upload a text-based PDF instead."));
        } else if (outcome.getWordCount() < 60) {
            candidates.add(entry("HIGH", "Your resume appears very sparse. Add your education, skills, "
                    + "projects and certifications so recruiters can quickly understand your background."));
        }

        if (!projectsFound) {
            candidates.add(entry("HIGH", "Add a Projects section — real projects are the strongest signal "
                    + "on a student resume."));
        } else if (bullets.isEmpty()) {
            candidates.add(entry("HIGH", "A Projects section was found but no descriptive bullets. Add 2–3 "
                    + "bullets per project, each starting with an action verb."));
        }

        if (!skillsFound) {
            candidates.add(entry("HIGH", "Add a Skills section listing the technologies and tools you "
                    + "actually use."));
        }

        if (projectsFound && !bullets.isEmpty() && measurable == 0) {
            candidates.add(entry("HIGH", "Add measurable results to your project descriptions where you can "
                    + "support them (e.g. number of users, records handled, latency improvement)."));
        }

        // ---------------- MEDIUM ----------------
        if (!contact.isEmailPresent()) {
            candidates.add(entry("MEDIUM", "Add a clear professional email address at the top of your resume."));
        }
        if (!contact.isPhonePresent()) {
            candidates.add(entry("MEDIUM", "Add a phone number at the top of your resume."));
        }
        if (!contact.isLinkedinPresent()) {
            candidates.add(entry("MEDIUM", "Add a LinkedIn profile link."));
        }
        if (!contact.isGithubPresent()) {
            candidates.add(entry("MEDIUM", "Add a GitHub link pointing to a few sample repositories."));
        }
        if (outcome.getPageCount() >= 3) {
            candidates.add(entry("MEDIUM", "Consider trimming to two concise pages or fewer; recruiters scan "
                    + "student resumes quickly."));
        }
        if (!experienceFound && !projectsFound) {
            candidates.add(entry("MEDIUM", "Fresher resumes can skip work experience, but try to add internships "
                    + "or strong projects so the resume is not thin."));
        }
        if (!bullets.isEmpty() && action * 2 < bullets.size()) {
            candidates.add(entry("MEDIUM", "Rewrite bullets to start with strong action verbs such as Built, "
                    + "Developed, Implemented, Reduced."));
        }
        if (firstPerson > 0) {
            candidates.add(entry("MEDIUM", "Avoid starting bullets with “I” or “my” — begin with the action "
                    + "instead."));
        }

        // ---------------- LOW ----------------
        if (!achievementsFound) {
            candidates.add(entry("LOW", "Consider adding an Achievements section if you have relevant awards "
                    + "or recognitions."));
        }
        if (!summaryFound) {
            candidates.add(entry("LOW", "A concise professional summary can quickly communicate who you are "
                    + "and what you are seeking."));
        }
        if (!porFound) {
            candidates.add(entry("LOW", "Consider adding a Positions of Responsibility section if you took on "
                    + "campus or club leadership."));
        }
        if (!contact.isPortfolioPresent()) {
            candidates.add(entry("LOW", "Add a portfolio link if you maintain one."));
        }
        if (tooLong > 0) {
            candidates.add(entry("LOW", "Some bullets are very long; aim for bullets under two lines that are "
                    + "quick to scan."));
        }
        if (!certificationsFound) {
            candidates.add(entry("LOW", "Add relevant certifications or completed coursework to strengthen "
                    + "your resume."));
        }

        candidates.sort(Comparator.comparingInt(e -> rankOf(e.getLevel())));

        return candidates.stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    private int rankOf(String level) {
        return switch (level) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private RecommendationEntry entry(String level, String text) {
        return RecommendationEntry.builder().level(level).text(text).build();
    }

    private boolean found(AnalysisOutcome outcome, ResumeSection section) {
        return outcome.getSections().stream()
                .anyMatch(s -> s.getName().equals(section.getLabel()) && s.isFound());
    }
}