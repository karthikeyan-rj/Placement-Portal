package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates bullet-level quality inside Projects / Experience / Achievements /
 * Positions of Responsibility sections. Every flag follows a documented rule:
 * action-verb starters, measurable outcomes, vague fillers, first-person
 * phrasing, excessive length and technology mentions. This deliberately avoids
 * "keyword counting" style scoring of the whole resume.
 */
@Component
public class ImpactAnalyzer {

    private static final List<String> ACTION_VERBS = List.of(
            "built", "developed", "implemented", "designed", "optimized", "optimised",
            "integrated", "deployed", "automated", "reduced", "improved", "created",
            "engineered", "architected", "refactored", "scaled", "led", "streamlined",
            "accelerated", "migrated", "launched", "delivered", "shipped", "configured",
            "authored", "researched", "analyzed", "analysed", "built", "mentored",
            "rebuilt", "revamped", "boosted", "expanded", "restructured");

    private static final List<String> VAGUE_STARTERS = List.of(
            "worked", "worked on", "making", "made", "done", "did", "helped",
            "assisted", "was", "were", "dealt", "handled", "responsible for",
            "worked with", "was part of", "got", "learning");

    private static final List<String> TECHNOLOGY_TERMS = List.of(
            "java", "spring", "react", "node", "python", "django", "flask",
            "typescript", "javascript", "sql", "postgres", "mysql", "mongodb",
            "docker", "kubernetes", "aws", "azure", "gcp", "git", "rest",
            "api", "graphql", "redis", "kafka", "tensorflow", "pytorch",
            "machine learning", "ml", "nlp", "html", "css", "c++", "c#",
            "c ", "android", "flutter", "firebase", "linux", "bash", "go ",
            "golang ", "rust", "php", "laravel", "express", "hibernate",
            "jpa", "kafka", "kafka", "swagger", "postman", "github actions",
            "jenkins", "terraform", "junit", "selenium", "opencv");

    private static final Pattern MEASURABLE_METRIC_PATTERN = Pattern.compile(
            "(?i)\\d{1,5}([.,]\\d+)?\\s*(%|percent|\\+|x|ms|sec|seconds?|mins?|hours?|users?|" +
                    "profiles?|records?|requests?|queries?|students?|recipients?|downloads?|" +
                    "views?|visits?|rows?|entries?|items?|orders?|transactions?|latency|" +
                    "response|throughput|uptime|revenue|stars?|kbps|gb|mb|kb|lpa)");

    public List<ProjectBullet> analyze(Map<ResumeSection, List<String>> blocks) {
        List<ProjectBullet> result = new ArrayList<>();
        for (ResumeSection section : List.of(
                ResumeSection.PROJECTS, ResumeSection.EXPERIENCE, ResumeSection.ACHIEVEMENTS,
                ResumeSection.POR)) {
            List<String> lines = blocks.getOrDefault(section, List.of());
            for (String line : lines) {
                ProjectBullet bullet = assess(line);
                if (bullet.text() != null) {
                    result.add(bullet);
                }
            }
        }
        return result;
    }

    private ProjectBullet assess(String line) {
        String text = line.trim();
        if (text.isEmpty() || looksLikePageNumber(text)) {
            return new ProjectBullet(null, false, false, false, false, false, false);
        }
        boolean actionVerb = startsWithActionVerb(text);
        boolean measurable = MEASURABLE_METRIC_PATTERN.matcher(text).find() || hasNumberWithMetric(text);
        boolean vague = isVague(text, actionVerb);
        boolean firstPerson = containsFirstPerson(text);
        boolean tooLong = wordCount(text) > 26;
        boolean mentionsTech = mentionsTechnology(text.toLowerCase());
        return new ProjectBullet(text, actionVerb, measurable, vague, firstPerson, tooLong, mentionsTech);
    }

    private boolean startsWithActionVerb(String text) {
        String[] words = text.split("\\s+");
        if (words.length == 0) {
            return false;
        }
        String first = words[0].toLowerCase().replaceAll("[^a-z']+", "");
        return ACTION_VERBS.contains(first);
    }

    private boolean hasNumberWithMetric(String text) {
        if (!text.matches(".*\\d.*")) {
            return false;
        }
        return text.matches("(?i).*\\d+\\s*(%|percent|\\.\\d+).*");
    }

    private boolean isVague(String text, boolean actionVerb) {
        String lower = text.toLowerCase();
        for (String starter : VAGUE_STARTERS) {
            if (lower.startsWith(starter)) {
                return true;
            }
        }
        // Extremely short, unquantified filler with no clear action verb.
        return !actionVerb && wordCount(text) < 8 && !text.contains(",") && isMostlyWords(text);
    }

    private boolean containsFirstPerson(String text) {
        String lower = " " + text.toLowerCase() + " ";
        return lower.contains(" i ") || lower.startsWith("i ") || lower.contains(" i've ")
                || lower.contains(" i have ") || lower.contains(" my ") || lower.contains(" myself ")
                || lower.startsWith("my ");
    }

    private boolean mentionsTechnology(String lower) {
        for (String term : TECHNOLOGY_TERMS) {
            if (lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikePageNumber(String text) {
        return text.matches("\\d{1,3}") || text.matches("page \\d+") || text.matches("-\\s*\\d+\\s*-");
    }

    private int wordCount(String text) {
        String[] words = text.trim().split("\\s+");
        return words.length == 1 && words[0].isEmpty() ? 0 : words.length;
    }

    private boolean isMostlyWords(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        return letters > 0 && letters >= text.replaceAll("\\s+", "").length() * 0.6;
    }
}