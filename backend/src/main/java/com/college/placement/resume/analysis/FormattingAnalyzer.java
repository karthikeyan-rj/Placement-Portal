package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatting / machine-readability heuristics. Produces explainable warnings
 * about page count, text density and extraction quality. This never claims to
 * know proprietary ATS internals.
 */
@Component
public class FormattingAnalyzer {

    /** Penalise resumes that look mostly like one giant line of text. */
    private static final int MAX_AVERAGE_LINE_LENGTH = 160;

    public AnalysisFormat assess(String text, int pageCount, int wordCount, List<SectionCheck> sections) {
        boolean extractable = wordCount > 0;
        double specialRatio = specialCharacterRatio(text);
        boolean standardHeadings = standardHeadings(sections);

        List<String> warnings = new ArrayList<>();
        if (wordCount == 0) {
            warnings.add("Text extraction returned no readable text. This PDF appears to be "
                    + "scanned or image-based, so text analysis is limited.");
        } else if (wordCount < 60) {
            warnings.add("The resume contains very little text, which may make it hard for "
                    + "an automated system to understand.");
        }
        if (pageCount >= 3) {
            warnings.add("Resume is " + pageCount + " pages; consider trimming to two concise "
                    + "pages or fewer for an entry-level resume.");
        }
        if (extractable && averageLineLength(text) > MAX_AVERAGE_LINE_LENGTH) {
            warnings.add("Some lines are very long; dense single-column paragraphs are harder "
                    + "for automated parsers to map to sections.");
        }
        if (specialRatio > 0.10) {
            warnings.add("Unusual characters were detected; prefer a clean, selectable-text PDF.");
        }

        return new AnalysisFormat(extractable, specialRatio, standardHeadings, warnings);
    }

    private boolean standardHeadings(List<SectionCheck> sections) {
        long coreFound = sections.stream()
                .filter(s -> isCoreHeading(s.getName()))
                .filter(SectionCheck::isFound)
                .count();
        return coreFound >= 2;
    }

    private boolean isCoreHeading(String name) {
        return List.of("Education", "Skills", "Projects", "Summary").contains(name);
    }

    private double specialCharacterRatio(String text) {
        if (text.isEmpty()) {
            return 1.0;
        }
        long unusual = text.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)
                        && ",.;:()%/-+_@()#[]".indexOf(c) < 0)
                .count();
        long total = Math.max(1, text.length());
        return (double) unusual / total;
    }

    private double averageLineLength(String text) {
        long nonEmptyChars = 0;
        long nonEmptyLines = 0;
        for (String line : text.split("\\R")) {
            if (!line.trim().isEmpty()) {
                nonEmptyChars += line.length();
                nonEmptyLines++;
            }
        }
        return nonEmptyLines == 0 ? 0 : (double) nonEmptyChars / nonEmptyLines;
    }

    public record AnalysisFormat(boolean extractable, double specialRatio,
                                 boolean standardHeadings, List<String> warnings) {
    }
}