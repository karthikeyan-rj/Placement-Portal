package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds standard resume section headings in extracted text. Matching is
 * tolerant: lines are trimmed, bullet points/numbering stripped, trailing
 * punctuation removed and casing ignored. Exact match against documented
 * variants avoids false positives from ordinary sentences.
 */
@Component
public class SectionDetector {

    /** Core sections whose content is used for bullet-level analysis. */
    private static final List<ResumeSection> BULLET_SECTIONS = List.of(
            ResumeSection.PROJECTS, ResumeSection.EXPERIENCE,
            ResumeSection.ACHIEVEMENTS, ResumeSection.POR);

    /** Extracts heading lines from the given extracted text. */
    public List<String> extractLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String cleaned = clean(line);
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    /**
     * Returns the canonical heading for a cleaned line, or {@code null}.
     * A line only counts as a heading when it exactly equals a documented
     * variant after normalisation, so "PROJECTS" or "Technical Projects"
     * match but a random sentence cannot.
     */
    public ResumeSection matchHeading(String cleanedLine) {
        String normalized = normalize(cleanedLine);
        if (normalized.isEmpty() || wordCount(normalized) > 6) {
            return null;
        }
        for (ResumeSection section : ResumeSection.values()) {
            for (String variant : section.getVariants()) {
                if (normalized.equals(variant)) {
                    return section;
                }
            }
        }
        return null;
    }

    /**
     * Cleans the leading label away from a line, keeping the text for further
     * analysis (bullets, project lines). Preserves the first 260 characters.
     */
    public String cleanForContent(String rawLine) {
        String cleaned = clean(rawLine);
        return cleaned.length() > 260 ? cleaned.substring(0, 260) : cleaned;
    }

    /** Normalises a raw line: trims, strips bullets/numbering/punctuation. */
    private String clean(String rawLine) {
        String t = rawLine.replace("\u0000", "").trim();
        t = t.replaceFirst("^[\\s]*[•·\\u2022\\u00B7\\-–—*]+\\s*", "");
        t = t.replaceFirst("^\\d+[\\.\\)]\\s*", "");
        t = t.replaceFirst("^[\\p{P}\\s]+", "");
        return t.replaceFirst("[\\s.,;:]+$", "").trim();
    }

    /** Lowercase, collapse whitespace, strip trailing punctuation. */
    private String normalize(String cleanedLine) {
        String n = cleanedLine.toLowerCase().replaceAll("[\\p{P}]+$", "").trim();
        return n.replaceAll("\\s+", " ").trim();
    }

    /**
     * Single pass over the text lines. Returns the found sections (in canonical
     * enum order) and, in document order, the per-section raw content blocks for
     * bullet-level analysis.
     */
    public Map.Entry<List<SectionCheck>, Map<ResumeSection, List<String>>> scan(String text) {
        List<String> lines = extractLines(text);

        Map<ResumeSection, List<String>> blocks = new LinkedHashMap<>();
        for (ResumeSection section : BULLET_SECTIONS) {
            blocks.put(section, new ArrayList<>());
        }

        ResumeSection current = null;
        for (String line : lines) {
            ResumeSection heading = matchHeading(line);
            if (heading != null) {
                current = heading;
                continue;
            }
            if (current != null && blocks.containsKey(current)) {
                blocks.get(current).add(line);
            }
        }

        List<SectionCheck> found = new ArrayList<>();
        for (ResumeSection section : ResumeSection.values()) {
            boolean present = lines.stream()
                    .anyMatch(l -> matchHeading(l) == section);
            found.add(new SectionCheck(section.getLabel(), present));
        }

        return Map.entry(found, blocks);
    }

    private int wordCount(String normalized) {
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split(" ").length;
    }
}