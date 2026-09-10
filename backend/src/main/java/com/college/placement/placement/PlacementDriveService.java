package com.college.placement.placement;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.PlacementDriveStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.company.Company;
import com.college.placement.company.CompanyRepository;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.placement.dto.*;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentAcademic;
import com.college.placement.student.StudentAcademicRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlacementDriveService {

    private final PlacementDriveRepository driveRepository;
    private final EligibilityCriteriaRepository eligibilityRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentAcademicRepository academicRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public Page<PlacementDriveResponse> getAllDrives(PlacementDriveStatus status, Pageable pageable) {
        Page<PlacementDrive> drives;
        if (status != null) {
            drives = driveRepository.findByStatusOrderByDriveDateDesc(status, pageable);
        } else {
            drives = driveRepository.findAllByOrderByDriveDateDesc(pageable);
        }
        List<PlacementDrive> content = drives.getContent();
        Map<Long, EligibilityCriteria> criteriaByDrive = new LinkedHashMap<>();
        if (!content.isEmpty()) {
            List<Long> ids = content.stream().map(PlacementDrive::getId).toList();
            for (EligibilityCriteria c : eligibilityRepository.findAllByPlacementDriveIdIn(ids)) {
                criteriaByDrive.put(c.getPlacementDrive().getId(), c);
            }
        }
        List<PlacementDriveResponse> responses = content.stream()
                .map(d -> toResponse(d, criteriaByDrive.get(d.getId())))
                .toList();
        return new PageImpl<>(responses, pageable, drives.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlacementDriveResponse getDriveById(Long id) {
        PlacementDrive drive = findDrive(id);
        return toResponse(drive);
    }

    @Transactional
    public PlacementDriveResponse createDrive(CreatePlacementDriveRequest request) {
        securityUtils.requireRole(Role.PO);

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.getCompanyId()));

        PlacementDrive drive = PlacementDrive.builder()
                .company(company)
                .jobRole(request.getJobRole())
                .packageLpa(request.getPackageLpa())
                .driveDate(request.getDriveDate() != null ? LocalDate.parse(request.getDriveDate()) : null)
                .registrationDeadline(request.getRegistrationDeadline() != null ? LocalDate.parse(request.getRegistrationDeadline()) : null)
                .location(request.getLocation())
                .jobDescription(request.getJobDescription())
                .status(PlacementDriveStatus.UPCOMING)
                .build();

        drive = driveRepository.save(drive);
        auditService.log("CREATE_PLACEMENT_DRIVE", "PlacementDrive", drive.getId(),
                company.getName() + " - " + drive.getJobRole());

        return toResponse(drive);
    }

    @Transactional
    public PlacementDriveResponse updateDriveStatus(Long id, PlacementDriveStatus status) {
        securityUtils.requireRole(Role.PO);

        PlacementDrive drive = findDrive(id);
        String oldStatus = drive.getStatus().name();
        drive.setStatus(status);
        drive = driveRepository.save(drive);

        auditService.log("UPDATE_DRIVE_STATUS", "PlacementDrive", id,
                oldStatus, status.name());

        return toResponse(drive);
    }

    @Transactional
    public void setEligibilityCriteria(Long driveId, CreateEligibilityRequest request) {
        securityUtils.requireRole(Role.PO);

        PlacementDrive drive = findDrive(driveId);

        EligibilityCriteria criteria = eligibilityRepository.findByPlacementDriveId(driveId)
                .orElseGet(() -> EligibilityCriteria.builder()
                        .placementDrive(drive)
                        .build());

        if (request.getMinCgpa() != null) criteria.setMinCgpa(request.getMinCgpa());
        if (request.getMaxActiveBacklogs() != null) criteria.setMaxActiveBacklogs(request.getMaxActiveBacklogs());
        if (request.getMinTenthPct() != null) criteria.setMinTenthPct(request.getMinTenthPct());
        if (request.getMinTwelfthPct() != null) criteria.setMinTwelfthPct(request.getMinTwelfthPct());
        if (request.getMinDiplomaPct() != null) criteria.setMinDiplomaPct(request.getMinDiplomaPct());

        if (request.getAllowedDepartmentIds() != null) {
            Set<Department> departments = new HashSet<>(departmentRepository.findAllById(request.getAllowedDepartmentIds()));
            criteria.setAllowedDepartments(departments);
        }

        eligibilityRepository.save(criteria);
        auditService.log("SET_ELIGIBILITY_CRITERIA", "EligibilityCriteria", criteria.getId(),
                "Drive: " + drive.getJobRole());
    }

    @Transactional(readOnly = true)
    public boolean checkEligibility(Long driveId, Long studentProfileId) {
        PlacementDrive drive = findDrive(driveId);

        EligibilityCriteria criteria = eligibilityRepository.findByPlacementDriveId(driveId).orElse(null);
        if (criteria == null) return true;

        StudentProfile profile = profileRepository.findById(studentProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", studentProfileId));

        Department studentDept = profile.getUser().getDepartment();
        if (studentDept == null) return false;

        if (!criteria.getAllowedDepartments().isEmpty() &&
            !criteria.getAllowedDepartments().contains(studentDept)) {
            return false;
        }

        StudentAcademic academic = academicRepository.findByStudentProfileId(studentProfileId);
        if (academic == null) return false;

        if (criteria.getMinCgpa() != null && academic.getCgpa() != null) {
            if (academic.getCgpa().compareTo(criteria.getMinCgpa()) < 0) return false;
        }

        if (criteria.getMaxActiveBacklogs() != null && academic.getActiveBacklogs() != null) {
            if (academic.getActiveBacklogs() > criteria.getMaxActiveBacklogs()) return false;
        }

        return true;
    }

    private PlacementDrive findDrive(Long id) {
        return driveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", id));
    }

    private PlacementDriveResponse toResponse(PlacementDrive drive) {
        EligibilityCriteria criteria = eligibilityRepository.findByPlacementDriveId(drive.getId()).orElse(null);
        return toResponse(drive, criteria);
    }

    private PlacementDriveResponse toResponse(PlacementDrive drive, EligibilityCriteria criteria) {
        EligibilityCriteriaResponse eligResponse = null;
        if (criteria != null) {
            eligResponse = EligibilityCriteriaResponse.builder()
                    .id(criteria.getId())
                    .minCgpa(criteria.getMinCgpa())
                    .maxActiveBacklogs(criteria.getMaxActiveBacklogs())
                    .minTenthPct(criteria.getMinTenthPct())
                    .minTwelfthPct(criteria.getMinTwelfthPct())
                    .minDiplomaPct(criteria.getMinDiplomaPct())
                    .allowedDepartmentIds(criteria.getAllowedDepartments().stream().map(Department::getId).toList())
                    .allowedDepartmentNames(criteria.getAllowedDepartments().stream().map(Department::getName).toList())
                    .build();
        }

        return PlacementDriveResponse.builder()
                .id(drive.getId())
                .companyId(drive.getCompany().getId())
                .companyName(drive.getCompany().getName())
                .companyType(drive.getCompany().getCompanyType() != null ? drive.getCompany().getCompanyType().name() : null)
                .jobRole(drive.getJobRole())
                .packageLpa(drive.getPackageLpa())
                .driveDate(drive.getDriveDate() != null ? drive.getDriveDate().toString() : null)
                .registrationDeadline(drive.getRegistrationDeadline() != null ? drive.getRegistrationDeadline().toString() : null)
                .location(drive.getLocation())
                .jobDescription(drive.getJobDescription())
                .status(drive.getStatus().name())
                .eligibilityCriteria(eligResponse)
                .build();
    }
}
