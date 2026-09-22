package com.college.placement.resume.analysis;

import java.util.List;
import java.util.Map;

/**
 * Deterministic outcome of a single analysis run. Contains everything the API
 * needs to respond and the service needs to persist. Extracted resume text is
 * deliberately not retained here beyond the analysis stage.
 */
public final class AnalysisOutcome {

    private final int pageCount;
    private final int wordCount;
    private final boolean textExtractable;
    private final double specialCharacterRatio;
    private final boolean standardHeadings;
    private final List<SectionCheck> sections;
    private final Map<ResumeSection, List<String>> sectionBlocks;
    private final List<ProjectBullet> bullets;
    private final ContactPresence contact;
    private final List<String> detectedSkills;
    private final List<String> warnings;

    private int profileScore;
    private int contentScore;
    private int impactScore;
    private int formattingScore;
    private int linksScore;
    private int readinessScore;
    private int atsCompatibility;
    private List<RecommendationEntry> recommendations;

    public AnalysisOutcome(int pageCount, int wordCount, boolean textExtractable,
                           double specialCharacterRatio, boolean standardHeadings,
                           List<SectionCheck> sections,
                           Map<ResumeSection, List<String>> sectionBlocks,
                           List<ProjectBullet> bullets, ContactPresence contact,
                           List<String> detectedSkills, List<String> warnings) {
        this.pageCount = pageCount;
        this.wordCount = wordCount;
        this.textExtractable = textExtractable;
        this.specialCharacterRatio = specialCharacterRatio;
        this.standardHeadings = standardHeadings;
        this.sections = sections;
        this.sectionBlocks = sectionBlocks;
        this.bullets = bullets;
        this.contact = contact;
        this.detectedSkills = detectedSkills;
        this.warnings = warnings;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getWordCount() {
        return wordCount;
    }

    public boolean isTextExtractable() {
        return textExtractable;
    }

    public double getSpecialCharacterRatio() {
        return specialCharacterRatio;
    }

    public boolean isStandardHeadings() {
        return standardHeadings;
    }

    public List<SectionCheck> getSections() {
        return sections;
    }

    public Map<ResumeSection, List<String>> getSectionBlocks() {
        return sectionBlocks;
    }

    public List<ProjectBullet> getBullets() {
        return bullets;
    }

    public ContactPresence getContact() {
        return contact;
    }

    public List<String> getDetectedSkills() {
        return detectedSkills;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getProfileScore() {
        return profileScore;
    }

    public int getContentScore() {
        return contentScore;
    }

    public int getImpactScore() {
        return impactScore;
    }

    public int getFormattingScore() {
        return formattingScore;
    }

    public int getLinksScore() {
        return linksScore;
    }

    public int getReadinessScore() {
        return readinessScore;
    }

    public int getAtsCompatibility() {
        return atsCompatibility;
    }

    public List<RecommendationEntry> getRecommendations() {
        return recommendations;
    }

    public void setCategoryScores(int profileScore, int contentScore, int impactScore,
                                  int formattingScore, int linksScore,
                                  int readinessScore, int atsCompatibility) {
        this.profileScore = profileScore;
        this.contentScore = contentScore;
        this.impactScore = impactScore;
        this.formattingScore = formattingScore;
        this.linksScore = linksScore;
        this.readinessScore = readinessScore;
        this.atsCompatibility = atsCompatibility;
    }

    public void setRecommendations(List<RecommendationEntry> recommendations) {
        this.recommendations = recommendations;
    }
}