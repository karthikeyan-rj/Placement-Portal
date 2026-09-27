package com.college.placement.mockinterview.controller;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.mockinterview.dto.*;
import com.college.placement.mockinterview.service.MockInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mock-interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    /** Modes, modules and real availability, so the UI never hardcodes content labels. */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<MockInterviewOptionsResponse>> getOptions() {
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getOptions()));
    }

    /**
     * Starts a session. The owner is taken from the JWT; no student identifier is
     * accepted from the client.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MockInterviewSessionResponse>> start(
            @Valid @RequestBody StartMockInterviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Mock interview started",
                mockInterviewService.startSession(request)));
    }

    /**
     * The student's unfinished interview, or {@code null} when there is none —
     * a normal state, so it is a 200 rather than a 404. Never creates a session.
     */
    @GetMapping("/me/active")
    public ResponseEntity<ApiResponse<MockInterviewSessionResponse>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getActiveSession()));
    }

    /** Interview history, server-side paginated. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PaginatedResponse<MockInterviewSummaryResponse>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(
                ApiResponse.success(mockInterviewService.getHistory(page, size, status)));
    }

    /** Session with its questions in fixed order. Ownership enforced server-side. */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<MockInterviewSessionResponse>> getSession(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getSession(sessionId)));
    }

    /** Post-interview breakdown. Rejected unless the session is COMPLETED. */
    @GetMapping("/{sessionId}/results")
    public ResponseEntity<ApiResponse<MockInterviewResultResponse>> getResults(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getResult(sessionId)));
    }

    /**
     * Saves one free-text answer. The client addresses a session question that the
     * server already chose; it cannot supply or change a question id.
     */
    @PutMapping("/{sessionId}/questions/{sessionQuestionId}/answer")
    public ResponseEntity<ApiResponse<MockInterviewQuestionView>> saveAnswer(
            @PathVariable Long sessionId,
            @PathVariable Long sessionQuestionId,
            @Valid @RequestBody SaveMockAnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Answer saved",
                mockInterviewService.saveAnswer(sessionId, sessionQuestionId, request.getStudentAnswer())));
    }

    /** Records the student's own confidence. Allowed during and after an interview. */
    @PutMapping("/{sessionId}/questions/{sessionQuestionId}/self-rating")
    public ResponseEntity<ApiResponse<MockInterviewQuestionView>> saveSelfRating(
            @PathVariable Long sessionId,
            @PathVariable Long sessionQuestionId,
            @RequestBody(required = false) SaveMockSelfRatingRequest request) {
        String rating = request == null ? null : request.getSelfRating();
        return ResponseEntity.ok(ApiResponse.success("Self assessment saved",
                mockInterviewService.saveSelfRating(sessionId, sessionQuestionId, rating)));
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<MockInterviewSummaryResponse>> complete(@PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Interview completed",
                mockInterviewService.completeSession(sessionId)));
    }

    @PostMapping("/{sessionId}/abandon")
    public ResponseEntity<ApiResponse<MockInterviewSummaryResponse>> abandon(@PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Interview abandoned",
                mockInterviewService.abandonSession(sessionId)));
    }
}
