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
        if (csvCode == null) {
            return Optional.empty();
        }
        String normalized = csvCode.trim().toUpperCase();
        String canonical = CSV_TO_DEPT.getOrDefault(normalized, normalized);

        Department dept = departmentRepository.findByNameIgnoreCase(canonical).orElse(null);
        if (dept == null && "MSCDATASC".equals(canonical)) {
            dept = departmentRepository.save(Department.builder().name(canonical).build());
        }
        return Optional.ofNullable(dept);
    }
}
