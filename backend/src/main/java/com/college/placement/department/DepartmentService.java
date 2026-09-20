package com.college.placement.department;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.department.dto.CreateDepartmentRequest;
import com.college.placement.department.dto.DepartmentAggregateResponse;
import com.college.placement.department.dto.DepartmentResponse;
import com.college.placement.department.dto.UpdateDepartmentRequest;
import com.college.placement.department.dto.UpdatePrConfigRequest;
import com.college.placement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final PrConfigRepository prConfigRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAllWithPrConfig().stream()
                .map(row -> toResponse((Department) row[0], row[1] != null ? ((PrConfig) row[1]).getMaxPrs() : 5))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getActiveDepartments() {
        return departmentRepository.findActiveWithPrConfig().stream()
                .map(row -> toResponse((Department) row[0], row[1] != null ? ((PrConfig) row[1]).getMaxPrs() : 5))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        Department dept = findDepartment(id);
        return toResponse(dept);
    }

    @Transactional(readOnly = true)
    public List<DepartmentAggregateResponse> getAggregates() {
        List<DepartmentAggregateResponse> all = departmentRepository.findAllAggregates().stream()
                .map(row -> DepartmentAggregateResponse.builder()
                        .departmentId(row.getDepartmentId())
                        .departmentName(row.getDepartmentName())
                        .active(row.getActive())
                        .prLimit(row.getPrLimit() != null ? row.getPrLimit() : 5)
                        .studentCount(row.getStudentCount())
                        .pcCount(row.getPcCount())
                        .prCount(row.getPrCount())
                        .build())
                .toList();

        Role currentRole = securityUtils.getCurrentUserRole();
        if (currentRole == Role.PC) {
            Long deptId = securityUtils.getCurrentUser().getDepartment() != null
                    ? securityUtils.getCurrentUser().getDepartment().getId() : null;
            if (deptId == null) {
                throw new ForbiddenException("You are not assigned to a department.");
            }
            return all.stream().filter(a -> deptId.equals(a.getDepartmentId())).toList();
        }
        if (currentRole != Role.PO) {
            throw new ForbiddenException("You do not have permission to view department aggregates.");
        }
        return all;
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        securityUtils.requireRole(com.college.placement.common.enums.Role.PO);

        if (departmentRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException("Department with name '" + request.getName() + "' already exists.");
        }

        Department dept = Department.builder()
                .name(request.getName().trim())
                .build();
        dept = departmentRepository.save(dept);

        PrConfig prConfig = PrConfig.builder()
                .department(dept)
                .maxPrs(5)
                .build();
        prConfigRepository.save(prConfig);

        auditService.log("CREATE_DEPARTMENT", "Department", dept.getId(), dept.getName());

        return toResponse(dept);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        securityUtils.requireRole(com.college.placement.common.enums.Role.PO);

        Department dept = findDepartment(id);

        if (request.getName() != null) {
            if (!dept.getName().equalsIgnoreCase(request.getName()) &&
                departmentRepository.existsByNameIgnoreCase(request.getName())) {
                throw new ConflictException("Department with name '" + request.getName() + "' already exists.");
            }
            String oldName = dept.getName();
            dept.setName(request.getName().trim());
            auditService.log("RENAME_DEPARTMENT", "Department", id, oldName, dept.getName());
        }

        if (request.getActive() != null) {
            boolean oldActive = dept.getActive();
            dept.setActive(request.getActive());
            auditService.log("UPDATE_DEPARTMENT_ACTIVE", "Department", id,
                    String.valueOf(oldActive), String.valueOf(request.getActive()));
        }

        departmentRepository.save(dept);
        return toResponse(dept);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        securityUtils.requireRole(com.college.placement.common.enums.Role.PO);

        Department dept = findDepartment(id);
        dept.setActive(false);
        departmentRepository.save(dept);

        auditService.log("DEACTIVATE_DEPARTMENT", "Department", id, dept.getName());
    }

    @Transactional(readOnly = true)
    public Integer getPrLimit(Long departmentId) {
        PrConfig config = prConfigRepository.findByDepartmentId(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("PR configuration", departmentId));
        return config.getMaxPrs();
    }

    @Transactional
    public void updatePrLimit(Long departmentId, UpdatePrConfigRequest request) {
        securityUtils.requireRole(com.college.placement.common.enums.Role.PO);

        findDepartment(departmentId);

        PrConfig config = prConfigRepository.findByDepartmentId(departmentId)
                .orElseGet(() -> PrConfig.builder()
                        .department(findDepartment(departmentId))
                        .maxPrs(5)
                        .build());

        Integer oldLimit = config.getMaxPrs();
        config.setMaxPrs(request.getMaxPrs());
        prConfigRepository.save(config);

        auditService.log("UPDATE_PR_LIMIT", "PrConfig", config.getId(),
                String.valueOf(oldLimit), String.valueOf(config.getMaxPrs()));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private DepartmentResponse toResponse(Department dept) {
        PrConfig prConfig = prConfigRepository.findByDepartmentId(dept.getId()).orElse(null);
        return toResponse(dept, prConfig != null ? prConfig.getMaxPrs() : 5);
    }

    private DepartmentResponse toResponse(Department dept, int prLimit) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .active(dept.getActive())
                .prLimit(prLimit)
                .build();
    }
}
