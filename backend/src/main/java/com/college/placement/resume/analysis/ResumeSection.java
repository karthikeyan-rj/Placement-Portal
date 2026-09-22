package com.college.placement.resume.analysis;

import java.util.List;

/**
 * Canonical resume sections recognised by the Resume Analyzer. Each enum value
 * carries tolerant heading variants (case-insensitive, punctuation removed) so
 * "PROJECT", "PROJECTS", "ACADEMIC PROJECTS" and "TECHNICAL PROJECTS" all map
 * to the same canonical section.
 */
public enum ResumeSection {

    SUMMARY("Summary", List.of(
            "summary", "professional summary", "career summary", "objective",
            "career objective", "profile summary", "professional profile", "profile")),
    EDUCATION("Education", List.of(
            "education", "educational qualification", "educational qualifications",
            "academic details", "academic background", "academic qualifications",
            "qualification")),
    SKILLS("Skills", List.of(
            "skills", "technical skills", "key skills", "skills summary",
            "technical skills summary", "core skills", "skills and abilities",
            "technical competencies")),
    PROJECTS("Projects", List.of(
            "projects", "project", "project details", "academic projects",
            "technical projects", "major projects", "mini projects", "mini project",
            "key projects", "projects undertaken")),
    EXPERIENCE("Experience", List.of(
            "experience", "work experience", "professional experience",
            "work history", "employment history", "internship", "internships",
            "internship experience", "industry experience", "training")),
    CERTIFICATIONS("Certifications", List.of(
            "certifications", "certification", "certificates", "certificate",
            "courses", "coursework and certifications", "courses and certifications",
            "certifications and training")),
    ACHIEVEMENTS("Achievements", List.of(
            "achievements", "accomplishments", "achievements and awards",
            "awards", "awards and achievements", "honors", "honors and awards",
            "honours", "accolades")),
    POR("Positions of Responsibility", List.of(
            "positions of responsibility", "position of responsibility",
            "por", "leadership", "leadership experience", "leadership roles",
            "campus involvement", "extracurricular")),
    PUBLICATIONS("Publications", List.of(
            "publications", "publication", "research publications", "research",
            "papers published", "research papers"));

    private final String label;
    private final List<String> variants;

    ResumeSection(String label, List<String> variants) {
        this.label = label;
        this.variants = variants;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getVariants() {
        return variants;
    }

    /** Canonical sections that matter most for a student/entry-level resume. */
    public static List<ResumeSection> coreSections() {
        return List.of(SUMMARY, EDUCATION, SKILLS, PROJECTS);
    }
}