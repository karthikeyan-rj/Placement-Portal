package com.college.placement.company;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.company.dto.CompanyResponse;
import com.college.placement.company.dto.CreateCompanyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CompanyResponse>>> searchCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<CompanyResponse> result = companyService.searchCompanies(
                search, PageRequest.of(page, size, Sort.by("name").ascending()));

        PaginatedResponse<CompanyResponse> paginated = PaginatedResponse.<CompanyResponse>builder()
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
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(companyService.getCompanyById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Company created", companyService.createCompany(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable Long id,
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company updated", companyService.updateCompany(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable Long id) {
        companyService.deactivateCompany(id);
        return ResponseEntity.ok(ApiResponse.success("Company deactivated", null));
    }
}
