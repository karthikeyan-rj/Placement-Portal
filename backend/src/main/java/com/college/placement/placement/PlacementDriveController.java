package com.college.placement.placement;

import com.college.placement.common.enums.PlacementDriveStatus;
import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.placement.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/placement-drives")
@RequiredArgsConstructor
public class PlacementDriveController {

    private final PlacementDriveService driveService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<PlacementDriveResponse>>> getAllDrives(
            @RequestParam(required = false) PlacementDriveStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PlacementDriveResponse> result = driveService.getAllDrives(status,
                PageRequest.of(page, size));

        PaginatedResponse<PlacementDriveResponse> paginated = PaginatedResponse.<PlacementDriveResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(paginated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlacementDriveResponse>> getDriveById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(driveService.getDriveById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlacementDriveResponse>> createDrive(
            @Valid @RequestBody CreatePlacementDriveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Placement drive created", driveService.createDrive(request)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PlacementDriveResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam PlacementDriveStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Status updated", driveService.updateDriveStatus(id, status)));
    }

    @PostMapping("/{id}/eligibility")
    public ResponseEntity<ApiResponse<Void>> setEligibility(
            @PathVariable Long id,
            @RequestBody CreateEligibilityRequest request) {
        driveService.setEligibilityCriteria(id, request);
        return ResponseEntity.ok(ApiResponse.success("Eligibility criteria set", null));
    }

    @GetMapping("/{driveId}/eligibility/{studentId}")
    public ResponseEntity<ApiResponse<Boolean>> checkEligibility(
            @PathVariable Long driveId,
            @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.success(driveService.checkEligibility(driveId, studentId)));
    }
}
