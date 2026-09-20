package com.college.placement.messaging;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.messaging.dto.CreateMessageRequest;
import com.college.placement.messaging.dto.MessageReactionRequest;
import com.college.placement.messaging.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @Valid @RequestBody CreateMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", messageService.sendMessage(request)));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<PaginatedResponse<MessageResponse>>> getSentMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MessageResponse> result = messageService.getSentMessages(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<PaginatedResponse<MessageResponse>>> getReceivedMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MessageResponse> result = messageService.getReceivedMessages(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long messageId) {
        messageService.markAsRead(messageId);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read", null));
    }

    @PostMapping("/{messageId}/reaction")
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody MessageReactionRequest request) {
        messageService.addReaction(messageId, request);
        return ResponseEntity.ok(ApiResponse.success("Reaction recorded", null));
    }

    @GetMapping("/{messageId}/my-reaction")
    public ResponseEntity<ApiResponse<com.college.placement.messaging.dto.MyReactionResponse>> getMyReaction(
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getMyReaction(messageId)));
    }

    @GetMapping("/{messageId}/analytics/{type}")
    public ResponseEntity<ApiResponse<Long>> getAnalytics(
            @PathVariable Long messageId,
            @PathVariable String type) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getMessageAnalytics(messageId, type)));
    }

    private PaginatedResponse<MessageResponse> toPaginated(Page<MessageResponse> page) {
        return PaginatedResponse.<MessageResponse>builder()
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
