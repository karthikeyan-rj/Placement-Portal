package com.college.placement.messaging;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.messaging.dto.ClarificationCountsResponse;
import com.college.placement.messaging.dto.ClarificationResponse;
import com.college.placement.messaging.dto.CreateClarificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClarificationController {

    private final ClarificationService clarificationService;

    @PostMapping("/messages/{messageId}/clarifications")
    public ResponseEntity<ApiResponse<ClarificationResponse>> createClarification(
            @PathVariable Long messageId,
            @Valid @RequestBody CreateClarificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Clarification created",
                        clarificationService.createClarification(messageId, request.getContent())));
    }

    @GetMapping("/messages/{messageId}/clarifications")
    public ResponseEntity<ApiResponse<PaginatedResponse<ClarificationResponse>>> listClarifications(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ClarificationResponse> result =
                clarificationService.listThreadsForMessage(messageId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    @GetMapping("/messages/{messageId}/clarification-counts")
    public ResponseEntity<ApiResponse<ClarificationCountsResponse>> getClarificationCounts(
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(clarificationService.getCounts(messageId)));
    }

    @GetMapping("/clarifications/{threadId}")
    public ResponseEntity<ApiResponse<ClarificationResponse>> getThread(
            @PathVariable Long threadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                clarificationService.getThread(threadId, PageRequest.of(page, size))));
    }

    @PostMapping("/clarifications/{threadId}/replies")
    public ResponseEntity<ApiResponse<ClarificationResponse>> replyToThread(
            @PathVariable Long threadId,
            @Valid @RequestBody CreateClarificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reply posted",
                clarificationService.replyToThread(threadId, request.getContent())));
    }

    @GetMapping("/clarifications/incoming")
    public ResponseEntity<ApiResponse<PaginatedResponse<ClarificationResponse>>> getIncoming(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ClarificationResponse> result =
                clarificationService.getIncoming(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    private PaginatedResponse<ClarificationResponse> toPaginated(Page<ClarificationResponse> page) {
        return PaginatedResponse.<ClarificationResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}