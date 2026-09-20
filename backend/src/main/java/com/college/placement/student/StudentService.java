package com.college.placement.student;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.dto.*;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentProfileRepository profileRepository;
    private final StudentAcademicRepository academicRepository;
    private final StudentProfessionalRepository professionalRepository;
    private final StudentPlacementInfoRepository placementInfoRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<?> searchStudents(String search, Long departmentId, String role,
                                  Boolean placementInterested, String placementStatus, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        Role currentRole = currentUser.getRole();

        Page<StudentProfileRepository.StudentListProjection> rows;
        if (currentRole == Role.PO) {
            if (departmentId != null) {
                rows = profileRepository.searchByDepartmentProjected(departmentId, search, role,
                        placementInterested, placementStatus, pageable);
            } else {
                rows = profileRepository.searchAllProjected(search, role,
                        placementInterested, placementStatus, pageable);
            }
            return rows.map(this::toResponseFromProjection);
        } else if (currentRole == Role.PC || currentRole == Role.PR) {
            Long userDeptId = currentUser.getDepartment() != null ? currentUser.getDepartment().getId() : null;
            if (userDeptId == null) {
                throw new ForbiddenException("You are not assigned to a department.");
            }
            if (departmentId != null && !departmentId.equals(userDeptId)) {
                throw new ForbiddenException("You can only access students within your department.");
            }
            rows = profileRepository.searchByDepartmentProjected(userDeptId, search, role,
                    placementInterested, placementStatus, pageable);
            if (currentRole == Role.PR) {
                return rows.map(this::toSummaryFromProjection);
            }
            return rows.map(this::toResponseFromProjection);
        } else {
            throw new ForbiddenException("You do not have permission to search students.");
        }
    }

    @Transactional(readOnly = true)
    public Object getStudentById(Long id) {
        StudentProfile profile = findProfile(id);
        validateStudentAccess(profile);
        if (securityUtils.isPR()) {
            return toSummaryResponse(profile);
        }
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public Object getStudentByUserId(Long userId) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", userId));
        validateStudentAccess(profile);
        if (securityUtils.isPR()) {
            return toSummaryResponse(profile);
        }
        StudentProfileRepository.StudentListProjection projected = profileRepository.findByUserIdProjected(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", userId));
        return toResponseFromProjection(projected);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfile() {
        User currentUser = securityUtils.getCurrentUser();
        StudentProfileRepository.StudentListProjection projected = profileRepository.findByUserIdProjected(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile for current user"));
        return toResponseFromProjection(projected);
    }

    @Transactional
    public StudentProfileResponse createStudentProfile(CreateStudentRequest request) {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        if (profileRepository.existsByUserId(request.getUserId())) {
            throw new ConflictException("Student profile already exists for this user.");
        }

        if (profileRepository.existsByRegisterNumber(request.getRegisterNumber())) {
            throw new ConflictException("Register number already in use: " + request.getRegisterNumber());
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        if (user.getDepartment() == null) {
            throw new BadRequestException("User must belong to a department to create a student profile.");
        }

        if (securityUtils.isPC()) {
            User currentUser = securityUtils.getCurrentUser();
            if (!currentUser.getDepartment().getId().equals(user.getDepartment().getId())) {
                throw new ForbiddenException("PC can only create profiles within their department.");
            }
        }

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .registerNumber(request.getRegisterNumber().trim())
                .phone(request.getPhone())
                .batch(request.getBatch())
                .section(request.getSection())
                .build();
        profile = profileRepository.save(profile);

        StudentAcademic academic = StudentAcademic.builder()
                .studentProfile(profile)
                .build();
        academicRepository.save(academic);

        StudentProfessional professional = StudentProfessional.builder()
                .studentProfile(profile)
                .build();
        professionalRepository.save(professional);

        StudentPlacementInfo placementInfo = StudentPlacementInfo.builder()
                .studentProfile(profile)
                .build();
        placementInfoRepository.save(placementInfo);

        auditService.log("CREATE_STUDENT_PROFILE", "StudentProfile", profile.getId(),
                profile.getRegisterNumber());

        return toResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateProfile(Long id, UpdateStudentProfileRequest request) {
        StudentProfile profile = findProfile(id);
        validateStudentEditAccess(profile);

        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getDateOfBirth() != null) {
            try {
                profile.setDateOfBirth(java.time.LocalDate.parse(request.getDateOfBirth()));
            } catch (java.time.format.DateTimeParseException e) {
                throw new BadRequestException("Date of birth must be in YYYY-MM-DD format.");
            }
        }
        if (request.getBatch() != null) profile.setBatch(request.getBatch());
        if (request.getSection() != null) profile.setSection(request.getSection());

        if (request.getPlacementInterested() != null) {
            StudentPlacementInfo placementInfo = placementInfoRepository.findByStudentProfileId(id);
            if (placementInfo == null) {
                placementInfo = StudentPlacementInfo.builder().studentProfile(profile).build();
            }
            placementInfo.setPlacementInterested(request.getPlacementInterested());
            placementInfoRepository.save(placementInfo);
        }

        profile = profileRepository.save(profile);
        auditService.log("UPDATE_STUDENT_PROFILE", "StudentProfile", profile.getId(),
                profile.getRegisterNumber());

        return toResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateAcademic(Long id, UpdateAcademicRequest request) {
        StudentProfile profile = findProfile(id);
        validateStudentEditAccess(profile);

        StudentAcademic academic = academicRepository.findByStudentProfileId(id);
        if (academic == null) {
            academic = StudentAcademic.builder().studentProfile(profile).build();
        }

        try {
            Map<String, Object> before = academicSnapshot(academic);

            if (request.getCgpa() != null) academic.setCgpa(request.getCgpa());
            if (request.getTenthPercentage() != null) academic.setTenthPercentage(request.getTenthPercentage());
            if (request.getTwelfthPercentage() != null) academic.setTwelfthPercentage(request.getTwelfthPercentage());
            if (request.getDiplomaPercentage() != null) academic.setDiplomaPercentage(request.getDiplomaPercentage());
            if (request.getActiveBacklogs() != null) academic.setActiveBacklogs(request.getActiveBacklogs());
            if (request.getHistoryOfBacklogs() != null) academic.setHistoryOfBacklogs(request.getHistoryOfBacklogs());

            academicRepository.save(academic);

            Map<String, Object> after = academicSnapshot(academic);
            auditService.log("UPDATE_STUDENT_ACADEMIC", "StudentAcademic", academic.getId(),
                    objectMapper.writeValueAsString(before), objectMapper.writeValueAsString(after));
        } catch (Exception e) {
            throw new BadRequestException("Failed to update academic information.");
        }

        return toResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateProfessional(Long id, UpdateProfessionalRequest request) {
        StudentProfile profile = findProfile(id);
        validateStudentEditAccess(profile);

        StudentProfessional professional = professionalRepository.findByStudentProfileId(id);
        if (professional == null) {
            professional = StudentProfessional.builder().studentProfile(profile).build();
        }

        if (request.getSkills() != null) professional.setSkills(request.getSkills());
        if (request.getCertifications() != null) professional.setCertifications(request.getCertifications());
        if (request.getProjects() != null) professional.setProjects(request.getProjects());
        if (request.getResumeUrl() != null) professional.setResumeUrl(request.getResumeUrl());
        if (request.getGithubUrl() != null) professional.setGithubUrl(request.getGithubUrl());
        if (request.getLinkedinUrl() != null) professional.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getPortfolioUrl() != null) professional.setPortfolioUrl(request.getPortfolioUrl());

        professionalRepository.save(professional);
        auditService.log("UPDATE_STUDENT_PROFESSIONAL", "StudentProfessional", professional.getId(),
                professional.getResumeUrl());

        return toResponse(profile);
    }

    private StudentProfile findProfile(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", id));
    }

    private Map<String, Object> academicSnapshot(StudentAcademic academic) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("tenthPercentage", academic.getTenthPercentage());
        snapshot.put("twelfthPercentage", academic.getTwelfthPercentage());
        snapshot.put("diplomaPercentage", academic.getDiplomaPercentage());
        snapshot.put("cgpa", academic.getCgpa());
        snapshot.put("activeBacklogs", academic.getActiveBacklogs());
        snapshot.put("historyOfBacklogs", academic.getHistoryOfBacklogs());
        return snapshot;
    }

    private void validateStudentAccess(StudentProfile profile) {
        Role role = securityUtils.getCurrentUserRole();
        if (role == Role.PO) return;
        if (role == Role.STUDENT) {
            User currentUser = securityUtils.getCurrentUser();
            if (!currentUser.getId().equals(profile.getUser().getId())) {
                throw new ForbiddenException("You can only access your own profile.");
            }
            return;
        }

        User currentUser = securityUtils.getCurrentUser();
        if (role == Role.PC || role == Role.PR) {
            if (currentUser.getDepartment() == null ||
                profile.getUser().getDepartment() == null ||
                !currentUser.getDepartment().getId().equals(profile.getUser().getDepartment().getId())) {
                throw new ForbiddenException("You can only access students within your department.");
            }
        }
    }

    private void validateStudentEditAccess(StudentProfile profile) {
        Role role = securityUtils.getCurrentUserRole();
        if (role == Role.PO || role == Role.PC) {
            validateStudentAccess(profile);
            return;
        }
        if (role == Role.STUDENT) {
            User currentUser = securityUtils.getCurrentUser();
            if (!currentUser.getId().equals(profile.getUser().getId())) {
                throw new ForbiddenException("You can only edit your own profile.");
            }
            return;
        }
        throw new ForbiddenException("PR cannot edit student data.");
    }

    private StudentProfileResponse toResponse(StudentProfile profile) {
        return toResponse(profile,
                academicRepository.findByStudentProfileId(profile.getId()),
                professionalRepository.findByStudentProfileId(profile.getId()),
                placementInfoRepository.findByStudentProfileId(profile.getId()));
    }

    private StudentProfileResponse toResponse(StudentProfile profile,
                                              StudentAcademic academic,
                                              StudentProfessional professional,
                                              StudentPlacementInfo placementInfo) {
        StudentProfileResponse.StudentProfileResponseBuilder builder = StudentProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .userName(profile.getUser().getName())
                .userEmail(profile.getUser().getEmail())
                .registerNumber(profile.getRegisterNumber())
                .phone(profile.getPhone())
                .dateOfBirth(profile.getDateOfBirth() != null ? profile.getDateOfBirth().toString() : null)
                .departmentId(profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getId() : null)
                .departmentName(profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : null)
                .batch(profile.getBatch())
                .section(profile.getSection());

        if (academic != null) {
            builder.tenthPercentage(academic.getTenthPercentage())
                    .twelfthPercentage(academic.getTwelfthPercentage())
                    .diplomaPercentage(academic.getDiplomaPercentage())
                    .cgpa(academic.getCgpa())
                    .activeBacklogs(academic.getActiveBacklogs())
                    .historyOfBacklogs(academic.getHistoryOfBacklogs());
        }

        if (professional != null) {
            builder.skills(professional.getSkills())
                    .certifications(professional.getCertifications())
                    .projects(professional.getProjects())
                    .resumeUrl(professional.getResumeUrl())
                    .githubUrl(professional.getGithubUrl())
                    .linkedinUrl(professional.getLinkedinUrl())
                    .portfolioUrl(professional.getPortfolioUrl());
        }

        if (placementInfo != null) {
            builder.placementInterested(placementInfo.getPlacementInterested())
                    .placementStatus(placementInfo.getPlacementStatus().name())
                    .interviewsAttended(placementInfo.getInterviewsAttended())
                    .placedCompanyId(placementInfo.getPlacedCompany() != null ? placementInfo.getPlacedCompany().getId() : null)
                    .placedCompanyName(placementInfo.getPlacedCompany() != null ? placementInfo.getPlacedCompany().getName() : null)
                    .packageLpa(placementInfo.getPackageLpa());
        }

        return builder.build();
    }

    private StudentSummaryResponse toSummaryResponse(StudentProfile profile) {
        return toSummaryResponse(profile,
                academicRepository.findByStudentProfileId(profile.getId()),
                placementInfoRepository.findByStudentProfileId(profile.getId()));
    }

    private StudentSummaryResponse toSummaryResponse(StudentProfile profile,
                                                     StudentAcademic academic,
                                                     StudentPlacementInfo placementInfo) {
        StudentSummaryResponse.StudentSummaryResponseBuilder builder = StudentSummaryResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .userName(profile.getUser().getName())
                .userEmail(profile.getUser().getEmail())
                .registerNumber(profile.getRegisterNumber())
                .departmentId(profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getId() : null)
                .departmentName(profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : null);

        if (academic != null) {
            builder.cgpa(academic.getCgpa());
        }

        if (placementInfo != null) {
            builder.placementInterested(placementInfo.getPlacementInterested())
                    .placementStatus(placementInfo.getPlacementStatus().name());
        }

        return builder.build();
    }

    private StudentProfileResponse toResponseFromProjection(StudentProfileRepository.StudentListProjection r) {
        return StudentProfileResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userName(r.getUserName())
                .userEmail(r.getUserEmail())
                .registerNumber(r.getRegisterNumber())
                .phone(r.getPhone())
                .dateOfBirth(r.getDateOfBirth())
                .departmentId(r.getDepartmentId())
                .departmentName(r.getDepartmentName())
                .batch(r.getBatch())
                .section(r.getSection())
                .tenthPercentage(r.getTenthPercentage())
                .twelfthPercentage(r.getTwelfthPercentage())
                .diplomaPercentage(r.getDiplomaPercentage())
                .cgpa(r.getCgpa())
                .activeBacklogs(r.getActiveBacklogs())
                .historyOfBacklogs(r.getHistoryOfBacklogs())
                .skills(r.getSkills())
                .certifications(r.getCertifications())
                .projects(r.getProjects())
                .resumeUrl(r.getResumeUrl())
                .githubUrl(r.getGithubUrl())
                .linkedinUrl(r.getLinkedinUrl())
                .portfolioUrl(r.getPortfolioUrl())
                .placementInterested(r.getPlacementInterested())
                .placementStatus(r.getPlacementStatus())
                .interviewsAttended(r.getInterviewsAttended())
                .placedCompanyId(r.getPlacedCompanyId())
                .placedCompanyName(r.getPlacedCompanyName())
                .packageLpa(r.getPackageLpa())
                .build();
    }

    private StudentSummaryResponse toSummaryFromProjection(StudentProfileRepository.StudentListProjection r) {
        return StudentSummaryResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userName(r.getUserName())
                .userEmail(r.getUserEmail())
                .registerNumber(r.getRegisterNumber())
                .departmentId(r.getDepartmentId())
                .departmentName(r.getDepartmentName())
                .cgpa(r.getCgpa())
                .placementInterested(r.getPlacementInterested())
                .placementStatus(r.getPlacementStatus())
                .build();
    }
}
