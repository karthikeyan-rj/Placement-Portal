package com.college.placement.publicapi;

import com.college.placement.department.DepartmentRepository;
import com.college.placement.department.dto.DepartmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final DepartmentRepository departmentRepository;

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentResponse>> getActiveDepartments() {
        List<DepartmentResponse> departments = departmentRepository.findByActiveTrue().stream()
                .map(dept -> DepartmentResponse.builder()
                        .id(dept.getId())
                        .name(dept.getName())
                        .active(dept.getActive())
                        .build())
                .toList();
        return ResponseEntity.ok(departments);
    }
}
