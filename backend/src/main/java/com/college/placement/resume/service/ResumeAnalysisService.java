package com.college.placement.resume.service;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.resume.analysis.AnalysisOutcome;
import com.college.placement.resume.analysis.ResumeAnalysisEngine;
import com.college.placement.resume.domain.ResumeAnalysis;
import com.college.placement.resume.dto.ResumeAnalysisResponse;
import com.college.placement.resume.dto.ResumeAnalysisSummaryResponse;
import com.college.placement.resume.repository.ResumeAnalysisRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Owning service for the Resume Analyzer. Current user is always derived from
 * the JWT; the frontend can never specify another student's profile.
 *
 * Privacy contract: extracted resume text is processed in memory and discarded;
 * audit entries carry only ids. No resume content, email or phone is logged.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ResumeAnalysisRepository repository;
    private final StudentProfileRepository studentProfileRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final ResumeAnalysisEngine analysisEngine;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public ResumeAnalysisResponse analyze(MultipartFile file) {
        securityUtils.requireAnyRole(Role.STUDENT, Role.PR);
        StudentProfile profile = currentProfile();
        validateUpload(file);

        long started = System.nanoTime();
        PdfTextExtractor.ExtractionResult extraction = pdfTextExtractor.extract(byteArray(file));
        long extractedAt = System.nanoTime();

        AnalysisOutcome outcome = analysisEngine.analyze(extraction.text(), extraction.pageCount());
        long analyzedAt = System.nanoTime();

        ResumeAnalysis saved = repository.save(toEntity(profile, file, extraction, outcome));
        auditService.log("RESUME_ANALYZED", "ResumeAnalysis", saved.getId(), null,
                "studentProfileId=" + profile.getId() + ",analysisId=" + saved.getId());
        long persistedAt = System.nanoTime();

        log.info("ResumeAnalysis id={} extract={}ms analyze={}ms persist={}ms",
                saved.getId(),
                (extractedAt - started) / 1_000_000L,
                (analyzedAt - extractedAt) / 1_000_000L,
                (persistedAt - analyzedAt) / 1_000_000L);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ResumeAnalysisSummaryResponse> history() {
        securityUtils.requireAnyRole(Role.STUDENT, Role.PR);
        StudentProfile profile = currentProfile();
        return repository.findTop20ByStudentProfileIdOrderByCreatedAtDesc(profile.getId())
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ResumeAnalysisResponse getDetail(Long analysisId) {
        securityUtils.requireAnyRole(Role.STUDENT, Role.PR);
        StudentProfile profile = currentProfile();
        ResumeAnalysis analysis = repository.findByIdAndStudentProfileId(analysisId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found."));
        return toResponse(analysis);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a PDF file to analyze.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files are supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Resume must be smaller than 5 MB.");
        }
        byte[] head = new byte[5];
        try {
            int read = file.getInputStream().read(head);
            if (read < 5 || !new String(head).startsWith("%PDF-")) {
                throw new BadRequestException("The file does not look like a valid PDF.");
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file. Please try again.");
        }
    }

    private byte[] byteArray(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file. Please try again.");
        }
    }

    private StudentProfile currentProfile() {
        Long userId = securityUtils.getCurrentUserId();
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for the current user."));
    }

    private ResumeAnalysis toEntity(StudentProfile profile, MultipartFile file,
                                    PdfTextExtractor.ExtractionResult extraction,
                                    AnalysisOutcome outcome) {
        return ResumeAnalysis.builder()
                .studentProfileId(profile.getId())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .pageCount(extraction.pageCount())
                .overallReadiness(outcome.getReadinessScore())
                .atsCompatibility(outcome.getAtsCompatibility())
                .profileScore(outcome.getProfileScore())
                .contentScore(outcome.getContentScore())
                .impactScore(outcome.getImpactScore())
                .formattingScore(outcome.getFormattingScore())
                .linksScore(outcome.getLinksScore())
                .detectedSections(outcome.getSections())
                .contactChecks(outcome.getContact())
                .detectedSkills(outcome.getDetectedSkills())
                .recommendations(outcome.getRecommendations())
                .warnings(outcome.getWarnings())
                .build();
    }

    private ResumeAnalysisSummaryResponse toSummary(ResumeAnalysis analysis) {
        return ResumeAnalysisSummaryResponse.builder()
                .id(analysis.getId())
                .fileName(analysis.getFileName())
                .pageCount(analysis.getPageCount())
                .readinessScore(analysis.getOverallReadiness())
                .createdAt(analysis.getCreatedAt())
                .build();
    }

    private ResumeAnalysisResponse toResponse(ResumeAnalysis analysis) {
        return ResumeAnalysisResponse.builder()
                .id(analysis.getId())
                .fileName(analysis.getFileName())
                .fileSize(analysis.getFileSize())
                .pageCount(analysis.getPageCount())
                .readinessScore(analysis.getOverallReadiness())
                .atsCompatibility(analysis.getAtsCompatibility())
                .categoryScores(ResumeAnalysisResponse.CategoryScores.builder()
                        .profileCompleteness(analysis.getProfileScore())
                        .contentQuality(analysis.getContentScore())
                        .impact(analysis.getImpactScore())
                        .formatting(analysis.getFormattingScore())
                        .professionalLinks(analysis.getLinksScore())
                        .build())
                .sections(analysis.getDetectedSections())
                .contactChecks(analysis.getContactChecks())
                .detectedSkills(analysis.getDetectedSkills())
                .warnings(analysis.getWarnings())
                .recommendations(analysis.getRecommendations())
                .createdAt(analysis.getCreatedAt())
                .build();
    }
}