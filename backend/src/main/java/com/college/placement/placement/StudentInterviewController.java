package com.college.placement.placement;

import com.college.placement.common.dto.ApiResponse;
import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.placement.dto.CreateInterviewRequest;
import com.college.placement.placement.dto.StudentInterviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class StudentInterviewController {

    private final StudentInterviewService interviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentInterviewResponse>>> getMyInterviews() {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.getMyInterviews(PageRequest.of(0, 100)).getContent()));
    }

    @GetMapping("/student/{studentProfileId}")
    public ResponseEntity<ApiResponse<List<StudentInterviewResponse>>> getInterviewsByStudent(
            @PathVariable Long studentProfileId) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.getInterviewsByStudent(studentProfileId)));
    }

    @GetMapping("/drive/{driveId}")
    public ResponseEntity<ApiResponse<List<StudentInterviewResponse>>> getInterviewsByDrive(
            @PathVariable Long driveId) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.getInterviewsByDrive(driveId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentInterviewResponse>> recordInterview(
            @Valid @RequestBody CreateInterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Interview recorded", interviewService.recordInterview(request)));
    }
}
