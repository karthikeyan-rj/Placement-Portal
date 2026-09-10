package com.college.placement.accesscode;

import com.college.placement.accesscode.dto.GenerateAccessCodesResponse;
import com.college.placement.common.dto.ApiResponse;
import com.college.placement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access-codes")
@RequiredArgsConstructor
public class AccessCodeController {

    private final AccessCodeService accessCodeService;
    private final SecurityUtils securityUtils;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('PO')")
    public ResponseEntity<ApiResponse<GenerateAccessCodesResponse>> generate() {
        Long poId = securityUtils.getCurrentUserId();
        GenerateAccessCodesResponse response = accessCodeService.generateCodes(poId);
        return ResponseEntity.ok(ApiResponse.success("Access codes generated", response));
    }
}
