package com.college.placement.user;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.enums.Role;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.dto.CreateUserRequest;
import com.college.placement.user.dto.UpdateUserRoleRequest;
import com.college.placement.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<UserResponse>>> searchUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserResponse> result = userService.searchUsers(
                search, role, departmentId, PageRequest.of(page, size, Sort.by("name").ascending()));

        PaginatedResponse<UserResponse> paginated = PaginatedResponse.<UserResponse>builder()
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
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", userService.createUser(request)));
    }

    @PutMapping("/{id}/assign-pc")
    public ResponseEntity<ApiResponse<UserResponse>> assignPc(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long departmentId = body.get("departmentId");
        return ResponseEntity.ok(ApiResponse.success("PC assigned successfully",
                userService.assignPcToDepartment(id, departmentId)));
    }

    @PutMapping("/{id}/promote-pr")
    public ResponseEntity<ApiResponse<UserResponse>> promoteToPr(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Student promoted to PR",
                userService.promoteToPr(id)));
    }

    @PutMapping("/{id}/demote-student")
    public ResponseEntity<ApiResponse<UserResponse>> demoteToStudent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PR demoted to Student",
                userService.demoteToStudent(id)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        securityUtils.requireRole(Role.PO);
        return ResponseEntity.ok(ApiResponse.success(userService.getStats()));
    }
}
