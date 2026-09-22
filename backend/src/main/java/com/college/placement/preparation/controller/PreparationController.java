package com.college.placement.preparation.controller;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.preparation.dto.*;
import com.college.placement.preparation.service.PreparationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/preparation")
@RequiredArgsConstructor
public class PreparationController {

    private final PreparationService preparationService;

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<PrepModuleResponse>>> getModules() {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getModuleList()));
    }

    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<PrepModuleDetailResponse>> getModuleDetail(@PathVariable Long moduleId) {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getModuleDetail(moduleId)));
    }

    @GetMapping("/topics/{topicId}")
    public ResponseEntity<ApiResponse<PrepTopicDetailResponse>> getTopicDetail(@PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getTopicDetail(topicId)));
    }

    @GetMapping("/topics/{topicId}/questions")
    public ResponseEntity<ApiResponse<List<PrepQuestionResponse>>> getQuestions(@PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getQuestions(topicId)));
    }

    @GetMapping("/me/progress")
    public ResponseEntity<ApiResponse<List<PrepProgressResponse>>> getMyProgress() {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getMyProgress()));
    }

    @PutMapping("/me/topics/{topicId}/progress")
    public ResponseEntity<ApiResponse<PrepProgressResponse>> updateMyProgress(
            @PathVariable Long topicId,
            @RequestBody(required = false) UpdatePrepProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Progress updated",
                preparationService.updateMyProgress(topicId, request)));
    }

    @GetMapping("/me/summary")
    public ResponseEntity<ApiResponse<PrepSummaryResponse>> getMySummary() {
        return ResponseEntity.ok(ApiResponse.success(preparationService.getMySummary()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PrepSearchResponse>> search(@RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(preparationService.search(q)));
    }
}