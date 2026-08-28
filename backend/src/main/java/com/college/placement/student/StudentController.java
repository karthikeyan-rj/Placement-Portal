package com.college.placement.student;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.student.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<StudentProfileResponse>>> searchStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<StudentProfileResponse> result = studentService.searchStudents(
                search, departmentId,
                PageRequest.of(page, size, Sort.by("register_number").ascending()));

        PaginatedResponse<StudentProfileResponse> paginated = PaginatedResponse.<StudentProfileResponse>builder()
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(studentService.getMyProfile()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(studentService.getStudentById(id)));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getStudentByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(studentService.getStudentByUserId(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentProfileResponse>> createStudentProfile(
            @Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student profile created", studentService.createStudentProfile(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateProfile(
            @PathVariable Long id,
            @RequestBody UpdateStudentProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", studentService.updateProfile(id, request)));
    }

    @PutMapping("/{id}/academic")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateAcademic(
            @PathVariable Long id,
            @RequestBody UpdateAcademicRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Academic info updated", studentService.updateAcademic(id, request)));
    }

    @PutMapping("/{id}/professional")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateProfessional(
            @PathVariable Long id,
            @RequestBody UpdateProfessionalRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Professional info updated", studentService.updateProfessional(id, request)));
    }
}
