package com.college.placement.report;

import com.college.placement.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/messages/{messageId}/csv")
    public void exportMessageAcknowledgements(@PathVariable Long messageId, HttpServletResponse response) throws Exception {
        reportService.exportMessageAcknowledgements(messageId, response);
    }

    @GetMapping("/students/csv")
    public void exportStudentsCsv(
            @RequestParam(required = false) Long departmentId,
            HttpServletResponse response) throws Exception {
        reportService.exportStudentsCsv(departmentId, response);
    }

    @GetMapping("/placement/csv")
    public void exportPlacementReport(HttpServletResponse response) throws Exception {
        reportService.exportPlacementReport(response);
    }
}
