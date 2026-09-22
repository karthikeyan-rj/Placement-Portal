package com.college.placement.resume.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic rule engine entry point. PDF extraction lives in
 * {@code PdfTextExtractor}; everything textual is processed here and produces a
 * single {@link AnalysisOutcome} that the service persists and the API returns.
 */
@Component
@RequiredArgsConstructor
public class ResumeAnalysisEngine {

    private final SectionDetector sectionDetector;
    private final ContactInspector contactInspector;
    private final SkillExtractor skillExtractor;
    private final FormattingAnalyzer formattingAnalyzer;
    private final ImpactAnalyzer impactAnalyzer;
    private final ResumeScoreCalculator scoreCalculator;
    private final RecommendationBuilder recommendationBuilder;

    public AnalysisOutcome analyze(String text, int pageCount) {
        Map.Entry<List<SectionCheck>, Map<ResumeSection, List<String>>> scan =
                sectionDetector.scan(text);
        List<SectionCheck> sections = scan.getKey();
        Map<ResumeSection, List<String>> blocks = scan.getValue();

        int wordCount = wordCount(text);
        List<ProjectBullet> bullets = impactAnalyzer.analyze(blocks);
        ContactPresence contact = contactInspector.inspect(text);
        List<String> skills = skillExtractor.extract(text);

        FormattingAnalyzer.AnalysisFormat format =
                formattingAnalyzer.assess(text, pageCount, wordCount, sections);

        List<String> warnings = new ArrayList<>(format.warnings());

        AnalysisOutcome outcome = new AnalysisOutcome(pageCount, wordCount,
                format.extractable(), format.specialRatio(), format.standardHeadings(),
                sections, blocks, bullets, contact, skills, warnings);

        scoreCalculator.compute(outcome);
        outcome.setRecommendations(recommendationBuilder.build(outcome));

        return outcome;
    }

    private int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}