package com.college.placement.resume.controller;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.resume.dto.ResumeAnalysisResponse;
import com.college.placement.resume.dto.ResumeAnalysisSummaryResponse;
import com.college.placement.resume.service.ResumeAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resume-analyzer")
@RequiredArgsConstructor
public class ResumeAnalyzerController {

    private final ResumeAnalysisService resumeAnalysisService;

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ResumeAnalysisResponse>> analyze(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Resume analyzed",
                resumeAnalysisService.analyze(file)));
    }

    @GetMapping("/me/history")
    public ResponseEntity<ApiResponse<List<ResumeAnalysisSummaryResponse>>> history() {
        return ResponseEntity.ok(ApiResponse.success(resumeAnalysisService.history()));
    }

    @GetMapping("/me/history/{analysisId}")
    public ResponseEntity<ApiResponse<ResumeAnalysisResponse>> detail(
            @PathVariable Long analysisId) {
        return ResponseEntity.ok(ApiResponse.success(resumeAnalysisService.getDetail(analysisId)));
    }
}