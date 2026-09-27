package com.college.placement.studentimport;

import com.college.placement.accesscode.AccessCodeGenerator;
import com.college.placement.accesscode.AccessCodeHasher;
import com.college.placement.audit.AuditService;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.validation.TceEmailValidator;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentCodeResolver;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentAccessCode;
import com.college.placement.student.StudentAccessCodeRepository;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.studentimport.dto.ImportAccessCodeRow;
import com.college.placement.studentimport.dto.ImportConfirmRequest;
import com.college.placement.studentimport.dto.ImportConfirmResponse;
import com.college.placement.studentimport.dto.ImportPreviewResponse;
import com.college.placement.studentimport.dto.ImportRowPreview;
import com.college.placement.studentimport.dto.ImportRowRequest;
import com.college.placement.studentimport.dto.ImportRowStatus;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PO-only bulk student onboarding from a roster CSV.
 *
 * The CSV only establishes authorization records (student_access_codes) â€” it
 * never creates User accounts or passwords. Students register afterwards with
 * the existing access-code flow: email + register number + one-time code +
 * their own chosen password. Identity fields (name, department, register
 * number) come from the roster record and are enforced by AuthService during
 * registration; the client can never override them.
 *
 * Two-stage workflow: preview() validates without writing; confirm()
 * re-validates server-side and imports only rows still READY. Only the
 * SHA-256 hash of each access code is persisted â€” plaintext codes are
 * returned exactly once in the confirm response and never logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentImportService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_CONFIRM_ROWS = 5000;
    private static final int PERSIST_BATCH_SIZE = 200;

    /** Logical header keys accepted in the CSV (normalized: lowercase, alnum only). */
    private static final Set<String> ALLOWED_HEADERS =
            Set.of("email", "name", "department", "registernumber", "regno");

    private final StudentAccessCodeRepository accessCodeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AccessCodeHasher accessCodeHasher;
    private final AccessCodeGenerator accessCodeGenerator;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** One parsed data row; line = 1-based CSV line number (header is line 1). */
    private record ParsedRow(int line, String email, String name,
                             String registerNumber, String department, String preError) {
    }

    // ------------------------------------------------------------------
    // Template
    // ------------------------------------------------------------------

    public byte[] templateCsv() {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"email", "name", "registerNumber", "department"});
        lines.add(new String[]{"student1@student.tce.edu", "Student One", "24C21031", "CSE"});
        lines.add(new String[]{"student2@student.tce.edu", "Student Two", "24C21032", "ECE"});
        return writeCsv(lines);
    }

    // ------------------------------------------------------------------
    // Stage 1: preview (no DB writes)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(MultipartFile file) {
        List<ParsedRow> rows = parseCsv(file);
        return classify(rows);
    }

    // ------------------------------------------------------------------
    // Stage 2: confirm (re-validate, then batch-import READY rows)
    // ------------------------------------------------------------------

    @Transactional
    public ImportConfirmResponse confirm(ImportConfirmRequest request) {
        List<ImportRowRequest> input = request == null || request.getRows() == null
                ? List.of() : request.getRows();
        if (input.isEmpty()) {
            throw new BadRequestException("No rows to import. Preview a CSV first.");
        }
        if (input.size() > MAX_CONFIRM_ROWS) {
            throw new BadRequestException("Too many rows in one import (max " + MAX_CONFIRM_ROWS + ").");
        }

        List<ParsedRow> parsed = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ImportRowRequest r = input.get(i);
            int line = r.getRowNumber() != null ? r.getRowNumber() : (i + 2);
            parsed.add(new ParsedRow(
                    line,
                    trimToEmpty(r.getEmail()),
                    trimToEmpty(r.getName()),
                    trimToEmpty(r.getRegisterNumber()),
                    trimToEmpty(r.getDepartment()),
                    null));
        }

        ImportPreviewResponse classified = classify(parsed);

        User po = securityUtils.getCurrentUser();
        Set<String> usedCodes = new HashSet<>();
        List<StudentAccessCode> toSave = new ArrayList<>();
        List<ImportAccessCodeRow> codes = new ArrayList<>();

        for (int i = 0; i < parsed.size(); i++) {
            ParsedRow row = parsed.get(i);
            ImportRowPreview preview = classified.getRows().get(i);
            if (preview.getStatus() != ImportRowStatus.READY) {
                continue;
            }
            // Reuse the single access-code generator + SHA-256 hasher; only the
            // hash is persisted, plaintext goes back to the caller once.
            String code = accessCodeGenerator.generateUnique(usedCodes);
            toSave.add(StudentAccessCode.builder()
                    .registerNumber(row.registerNumber())
                    .name(row.name())
                    .departmentCode(row.department())
                    .email(row.email())
                    .codeHash(accessCodeHasher.hash(code))
                    .createdBy(po)
                    .active(true)
                    .build());
            codes.add(ImportAccessCodeRow.builder()
                    .rowNumber(row.line())
                    .name(row.name())
                    .email(row.email())
                    .registerNumber(row.registerNumber())
                    .department(row.department())
                    .accessCode(code)
                    .build());
        }

        for (int i = 0; i < toSave.size(); i += PERSIST_BATCH_SIZE) {
            accessCodeRepository.saveAll(toSave.subList(i, Math.min(i + PERSIST_BATCH_SIZE, toSave.size())));
        }

        int imported = codes.size();
        int alreadyRegistered = countStatus(classified.getRows(), ImportRowStatus.ALREADY_REGISTERED);
        int alreadyAuthorized = countStatus(classified.getRows(), ImportRowStatus.ALREADY_AUTHORIZED);
        int skipped = countStatus(classified.getRows(), ImportRowStatus.INVALID)
                + countStatus(classified.getRows(), ImportRowStatus.DUPLICATE);

        // One audit event for the whole import â€” counts and actor only, never
        // access codes or student data.
        auditService.log("STUDENT_BULK_IMPORT", "StudentImport", null,
                "imported=" + imported + ", skipped=" + skipped
                        + ", alreadyRegistered=" + alreadyRegistered
                        + ", alreadyAuthorized=" + alreadyAuthorized);

        List<ImportRowPreview> errors = classified.getRows().stream()
                .filter(r -> r.getStatus() != ImportRowStatus.READY)
                .toList();

        log.info("Student CSV import by PO {}: imported={}, skipped={}, alreadyRegistered={}, alreadyAuthorized={}",
                po.getId(), imported, skipped, alreadyRegistered, alreadyAuthorized);

        return ImportConfirmResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .alreadyRegistered(alreadyRegistered)
                .alreadyAuthorized(alreadyAuthorized)
                .codes(codes)
                .errors(errors)
                .build();
    }

    // ------------------------------------------------------------------
    // CSV parsing
    // ------------------------------------------------------------------

    private List<ParsedRow> parseCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is empty.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Only .csv files are supported.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("File must be smaller than 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file.");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("CSV file is empty.");
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        if (content.trim().isEmpty()) {
            throw new BadRequestException("CSV file is empty.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new StringReader(content))) {
            String[] header = reader.readNext();
            if (header == null || isAllBlank(header)) {
                throw new BadRequestException("CSV file is missing a header row.");
            }

            Map<String, Integer> index = mapHeader(header);

            String[] record;
            int line = 1;
            while ((record = reader.readNext()) != null) {
                line++;
                if (isAllBlank(record)) {
                    continue;
                }
                String preError = null;
                if (record.length != header.length) {
                    preError = "Malformed row: expected " + header.length
                            + " columns, found " + record.length + ".";
                }
                rows.add(new ParsedRow(
                        line,
                        cell(record, index.get("email")),
                        cell(record, index.get("name")),
                        cell(record, index.get("registernumber")),
                        cell(record, index.get("department")),
                        preError));
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
            throw new BadRequestException("CSV file is malformed: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new BadRequestException("CSV file contains no data rows.");
        }
        return rows;
    }

    /**
     * Validates and normalizes the header row. All four logical columns are
     * required; unknown columns are rejected outright rather than silently
     * accepted, and duplicate columns are rejected.
     */
    private Map<String, Integer> mapHeader(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String raw = header[i] == null ? "" : header[i].trim();
            if (raw.isEmpty()) {
                throw new BadRequestException("CSV header contains an empty column name.");
            }
            String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!ALLOWED_HEADERS.contains(normalized)) {
                throw new BadRequestException("Unsupported column \"" + raw
                        + "\". Required columns: email, name, registerNumber, department.");
            }
            String logical = normalized.equals("regno") ? "registernumber" : normalized;
            if (index.containsKey(logical)) {
                throw new BadRequestException("Duplicate column \"" + raw + "\" in CSV header.");
            }
            index.put(logical, i);
        }
        for (String required : new String[]{"email", "name", "registernumber", "department"}) {
            if (!index.containsKey(required)) {
                throw new BadRequestException("Missing required column \"" + required
                        + "\". Required columns: email, name, registerNumber, department.");
            }
        }
        return index;
    }

    // ------------------------------------------------------------------
    // Row classification (shared by preview and confirm)
    // ------------------------------------------------------------------

    private ImportPreviewResponse classify(List<ParsedRow> rows) {
        // Batch DB lookups â€” one query per table, never one per row.
        Set<String> emails = new HashSet<>();
        Set<String> regNos = new HashSet<>();
        for (ParsedRow r : rows) {
            if (!r.email().isEmpty()) emails.add(r.email());
            if (!r.registerNumber().isEmpty()) regNos.add(r.registerNumber());
        }
        Set<String> existingEmails = new HashSet<>(
                userRepository.findEmailsIn(emails.stream().map(String::toLowerCase).toList()));
        Set<String> existingProfileRegs = normalizeRegs(
                studentProfileRegNos(regNos));
        Set<String> existingAccessRegs = normalizeRegs(accessCodeRegNos(regNos));

        // Departments resolved once against the DB (case-insensitive, never created).
        Map<String, Department> departments = new HashMap<>();
        for (Department d : departmentRepository.findAll()) {
            departments.put(d.getName().toUpperCase(Locale.ROOT), d);
        }

        List<ImportRowPreview> previews = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenRegs = new HashSet<>();

        for (ParsedRow row : rows) {
            ImportRowPreview preview = classifyRow(row, row.preError(), departments,
                    existingEmails, existingProfileRegs, existingAccessRegs,
                    seenEmails, seenRegs);
            previews.add(preview);
        }

        return ImportPreviewResponse.builder()
                .totalRows(rows.size())
                .validRows(countStatus(previews, ImportRowStatus.READY))
                .invalidRows(countStatus(previews, ImportRowStatus.INVALID))
                .duplicateRows(countStatus(previews, ImportRowStatus.DUPLICATE))
                .alreadyRegistered(countStatus(previews, ImportRowStatus.ALREADY_REGISTERED))
                .alreadyAuthorized(countStatus(previews, ImportRowStatus.ALREADY_AUTHORIZED))
                .rows(previews)
                .build();
    }

    private ImportRowPreview classifyRow(ParsedRow row, String preError,
                                          Map<String, Department> departments,
                                          Set<String> existingEmails,
                                          Set<String> existingProfileRegs,
                                          Set<String> existingAccessRegs,
                                          Set<String> seenEmails,
                                          Set<String> seenRegs) {
        String email = row.email();
        String name = row.name();
        String reg = row.registerNumber();
        String deptRaw = row.department();

        // 1. File-level structural error for this row.
        if (preError != null) {
            return rowPreview(row, ImportRowStatus.INVALID, preError);
        }

        // 2. Field validation, first error wins (email â†’ name â†’ regno â†’ department).
        String formatError = null;
        if (email.isEmpty()) {
            formatError = "Email is required.";
        } else if (!TceEmailValidator.isAllowedTceEmail(email)) {
            formatError = "Email must be a TCE address (name@tce.edu or name@<sub>.tce.edu).";
        } else if (name.isEmpty()) {
            formatError = "Name is required.";
        } else if (reg.isEmpty()) {
            formatError = "Register number is required.";
        } else if (!reg.matches(".*\\d.*")) {
            // Registration matches register numbers on their trailing digits â€”
            // a value with no digits could never complete registration.
            formatError = "Register number must contain digits.";
        } else if (deptRaw.isEmpty()) {
            formatError = "Department is required.";
        } else {
            String canonical = DepartmentCodeResolver.canonicalize(deptRaw);
            Department dept = departments.get(canonical);
            if (dept == null) {
                formatError = "Unknown department \"" + deptRaw + "\".";
            } else if (!Boolean.TRUE.equals(dept.getActive())) {
                formatError = "Department \"" + deptRaw + "\" is inactive.";
            }
        }
        if (formatError != null) {
            return rowPreview(row, ImportRowStatus.INVALID, formatError);
        }

        // 3. In-file duplicates (first occurrence wins; case-insensitive).
        boolean dupEmail = !seenEmails.add(email.toLowerCase(Locale.ROOT));
        boolean dupReg = !seenRegs.add(reg.toUpperCase(Locale.ROOT));
        if (dupEmail || dupReg) {
            String reason;
            if (dupEmail && dupReg) {
                reason = "Duplicate email and register number in file.";
            } else if (dupEmail) {
                reason = "Duplicate email in file.";
            } else {
                reason = "Duplicate register number in file.";
            }
            return rowPreview(row, ImportRowStatus.DUPLICATE, reason);
        }

        // 4. Existing DB records.
        boolean regEmailExists = existingEmails.contains(email.toLowerCase(Locale.ROOT));
        boolean regRegExists = existingProfileRegs.contains(reg.toUpperCase(Locale.ROOT));
        if (regEmailExists || regRegExists) {
            String reason;
            if (regEmailExists && regRegExists) {
                reason = "Email and register number already registered.";
            } else if (regEmailExists) {
                reason = "Email already registered.";
            } else {
                reason = "Register number already registered.";
            }
            return rowPreview(row, ImportRowStatus.ALREADY_REGISTERED, reason);
        }
        if (existingAccessRegs.contains(reg.toUpperCase(Locale.ROOT))) {
            return rowPreview(row, ImportRowStatus.ALREADY_AUTHORIZED,
                    "Already authorized for registration.");
        }

        return rowPreview(row, ImportRowStatus.READY, null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Collection<String> studentProfileRegNos(Set<String> regNos) {
        return regNos.isEmpty() ? List.of()
                : studentProfileRepository.findAllByRegisterNumberIn(regNos)
                        .stream().map(p -> p.getRegisterNumber()).toList();
    }

    private Collection<String> accessCodeRegNos(Set<String> regNos) {
        return regNos.isEmpty() ? List.of()
                : accessCodeRepository.findAllByRegisterNumberIn(regNos)
                        .stream().map(StudentAccessCode::getRegisterNumber).toList();
    }

    private Set<String> normalizeRegs(Collection<String> regs) {
        Set<String> out = new HashSet<>();
        for (String r : regs) {
            out.add(r.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static ImportRowPreview rowPreview(ParsedRow row, ImportRowStatus status, String error) {
        return ImportRowPreview.builder()
                .rowNumber(row.line())
                .email(row.email())
                .name(row.name())
                .registerNumber(row.registerNumber())
                .department(row.department())
                .status(status)
                .error(error)
                .build();
    }

    private static int countStatus(List<ImportRowPreview> rows, ImportRowStatus status) {
        int n = 0;
        for (ImportRowPreview r : rows) {
            if (r.getStatus() == status) n++;
        }
        return n;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cell(String[] record, Integer idx) {
        if (idx == null || idx < 0 || idx >= record.length || record[idx] == null) {
            return "";
        }
        return record[idx].trim();
    }

    private static boolean isAllBlank(String[] record) {
        if (record == null) return true;
        for (String c : record) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Builds a CSV with spreadsheet formula-injection protection: any value
     * starting with =, +, -, @, tab or CR is prefixed with a single quote so
     * Excel/Sheets treat it as text instead of a formula.
     */
    private byte[] writeCsv(List<String[]> lines) {
        StringWriter out = new StringWriter();
        try (CSVWriter writer = new CSVWriter(out)) {
            for (String[] line : lines) {
                String[] safe = new String[line.length];
                for (int i = 0; i < line.length; i++) {
                    safe[i] = sanitizeCsvCell(line[i]);
                }
                writer.writeNext(safe);
            }
            writer.flush();
        } catch (IOException e) {
            throw new BadRequestException("Could not build CSV.");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String sanitizeCsvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
