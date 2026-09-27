package com.college.placement.contact;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.contact.dto.ContactRequestCountsResponse;
import com.college.placement.contact.dto.ContactRequestResponse;
import com.college.placement.contact.dto.ContactRequestTargetResponse;
import com.college.placement.contact.dto.CreateContactRequest;
import com.college.placement.contact.dto.UpdateContactRequestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

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

    /**
     * Coordinators the caller is actually allowed to request contact with.
     * Resolved server-side from the caller's own scope, so the client never
     * enumerates the whole college and cannot widen its own options.
     */
    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<List<ContactRequestTargetResponse>>> getTargets() {
        return ResponseEntity.ok(ApiResponse.success(contactRequestService.getEligibleTargets()));
    }

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<ContactRequestCountsResponse>> getCounts(
            @RequestParam(defaultValue = "incoming") String view) {
        ContactRequestCountsResponse counts = "mine".equalsIgnoreCase(view.trim().toLowerCase(Locale.ROOT))
                ? contactRequestService.getSentCounts()
                : contactRequestService.getIncomingCounts();
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    /**
     * Status changes are submitted as a validated body rather than a query
     * parameter so an unknown value is rejected as a 400 by bean validation
     * and the transition itself is checked by the service.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ContactRequestResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContactRequestStatus request) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                contactRequestService.updateStatus(id, request)));
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
