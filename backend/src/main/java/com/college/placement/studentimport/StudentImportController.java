package com.college.placement.studentimport;

import com.college.placement.studentimport.dto.ImportConfirmRequest;
import com.college.placement.studentimport.dto.ImportConfirmResponse;
import com.college.placement.studentimport.dto.ImportPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * PO-only bulk onboarding of student authorization records from a roster CSV.
 *
 * Only Placement Officers may import. Preview validates without writing;
 * confirm re-validates server-side and imports only rows still READY. The CSV
 * establishes identity + one-time access codes — students create their own
 * passwords through the normal registration flow afterwards.
 */
@RestController
@RequestMapping("/api/students/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PO')")
public class StudentImportController {

    private static final String TEMPLATE_FILENAME = "student-import-template.csv";

    private final StudentImportService studentImportService;

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .headers(headers -> headers.setContentDisposition(ContentDisposition
                        .attachment().filename(TEMPLATE_FILENAME, StandardCharsets.UTF_8).build()))
                .body(studentImportService.templateCsv());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse preview(@RequestPart("file") MultipartFile file) {
        return studentImportService.preview(file);
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportConfirmResponse confirm(@RequestBody ImportConfirmRequest request) {
        return studentImportService.confirm(request);
    }
}