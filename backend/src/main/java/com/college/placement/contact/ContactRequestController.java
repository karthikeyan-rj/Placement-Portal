package com.college.placement.contact;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.contact.dto.ContactRequestResponse;
import com.college.placement.contact.dto.CreateContactRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact-requests")
@RequiredArgsConstructor
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactRequestResponse>> createContactRequest(
            @Valid @RequestBody CreateContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contact request created", contactRequestService.createContactRequest(request)));
    }

    @GetMapping("/incoming")
    public ResponseEntity<ApiResponse<PaginatedResponse<ContactRequestResponse>>> getIncomingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContactRequestResponse> result = contactRequestService.getIncomingRequests(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PaginatedResponse<ContactRequestResponse>>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContactRequestResponse> result = contactRequestService.getMyRequests(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(toPaginated(result)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ContactRequestResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam ContactRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                contactRequestService.updateStatus(id, status)));
    }

    private PaginatedResponse<ContactRequestResponse> toPaginated(Page<ContactRequestResponse> page) {
        return PaginatedResponse.<ContactRequestResponse>builder()
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
