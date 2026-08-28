package com.college.placement.department;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.department.dto.CreateDepartmentRequest;
import com.college.placement.department.dto.DepartmentResponse;
import com.college.placement.department.dto.UpdateDepartmentRequest;
import com.college.placement.department.dto.UpdatePrConfigRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllDepartments() {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getAllDepartments()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getActiveDepartments() {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getActiveDepartments()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getDepartmentById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully",
                departmentService.updateDepartment(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("Department deactivated successfully", null));
    }

    @PutMapping("/{id}/pr-config")
    public ResponseEntity<ApiResponse<Void>> updatePrLimit(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePrConfigRequest request) {
        departmentService.updatePrLimit(id, request);
        return ResponseEntity.ok(ApiResponse.success("PR limit updated successfully", null));
    }
}
