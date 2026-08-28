package com.college.placement.audit;

import com.college.placement.audit.dto.AuditLogResponse;
import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.common.enums.Role;
import com.college.placement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        securityUtils.requireRole(Role.PO);

        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        List<AuditLogResponse> responses = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        PaginatedResponse<AuditLogResponse> paginated = PaginatedResponse.<AuditLogResponse>builder()
                .content(responses)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(paginated));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        String details = null;
        if (log.getOldValue() != null || log.getNewValue() != null) {
            details = (log.getOldValue() != null ? "From: " + log.getOldValue() : "") +
                      (log.getNewValue() != null ? " To: " + log.getNewValue() : "");
        }
        return AuditLogResponse.builder()
                .id(log.getId())
                .userEmail(log.getPerformedBy() != null ? log.getPerformedBy().getEmail() : "—")
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(details)
                .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : null)
                .build();
    }
}
