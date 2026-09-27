package com.college.placement.department;

import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DepartmentCodeResolver {

    private final DepartmentRepository departmentRepository;

    private static final Map<String, String> CSV_TO_DEPT = new LinkedHashMap<>();

    static {
        CSV_TO_DEPT.put("AIML", "CSE-AIML");
        CSV_TO_DEPT.put("CIVIL", "CIVIL");
        CSV_TO_DEPT.put("CSBS", "CSBS");
        CSV_TO_DEPT.put("CSE", "CSE");
        CSV_TO_DEPT.put("ECE", "ECE");
        CSV_TO_DEPT.put("EEE", "EEE");
        CSV_TO_DEPT.put("IT", "IT");
        CSV_TO_DEPT.put("MECH", "MECHANICAL");
        CSV_TO_DEPT.put("MECT", "MECHATRONICS");
        CSV_TO_DEPT.put("MSCDATASC", "MSCDATASC");
    }

    public Optional<Department> resolve(String csvCode) {
        String canonical = canonicalize(csvCode);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }

        Department dept = departmentRepository.findByNameIgnoreCase(canonical).orElse(null);
        if (dept == null && "MSCDATASC".equals(canonical)) {
            dept = departmentRepository.save(Department.builder().name(canonical).build());
        }
        return Optional.ofNullable(dept);
    }

    /**
     * Strict resolution for validation flows (e.g. PO student CSV import):
     * resolves to an existing department only and NEVER creates one. Unknown
     * codes return empty so the caller can reject the row.
     */
    public Optional<Department> resolveStrict(String csvCode) {
        String canonical = canonicalize(csvCode);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }
        return departmentRepository.findByNameIgnoreCase(canonical);
    }

    /**
     * Canonicalizes a CSV department token to the DB department name without
     * touching the database. Case-insensitive; e.g. "cse" → "CSE",
     * "MECH" → "MECHANICAL", "Computer Science" stays uppercased for a
     * direct name match attempt by the caller.
     */
    public static String canonicalize(String csvCode) {
        if (csvCode == null) {
            return "";
        }
        String normalized = csvCode.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return "";
        }
        return CSV_TO_DEPT.getOrDefault(normalized, normalized);
    }
}
