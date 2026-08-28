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
    public Page<StudentProfileResponse> searchStudents(String search, Long departmentId, Pageable pageable) {
        Role role = securityUtils.getCurrentUserRole();

        Page<StudentProfile> profiles;
        if (role == Role.PO) {
            if (departmentId != null) {
                profiles = profileRepository.searchByDepartment(departmentId, search, pageable);
            } else {
                profiles = profileRepository.searchAll(search, pageable);
            }
        } else if (role == Role.PC || role == Role.PR) {
            Long userDeptId = securityUtils.getCurrentUser().getDepartment().getId();
            if (departmentId != null && !departmentId.equals(userDeptId)) {
                throw new ForbiddenException("You can only access students within your department.");
            }
            profiles = profileRepository.searchByDepartment(userDeptId, search, pageable);
        } else {
            throw new ForbiddenException("You do not have permission to search students.");
        }

        return profiles.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentById(Long id) {
        StudentProfile profile = findProfile(id);
        validateStudentAccess(profile);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentByUserId(Long userId) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", userId));
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfile() {
        User currentUser = securityUtils.getCurrentUser();
        StudentProfile profile = profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile for current user"));
        return toResponse(profile);
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
            profile.setDateOfBirth(java.time.LocalDate.parse(request.getDateOfBirth()));
        }
        if (request.getBatch() != null) profile.setBatch(request.getBatch());
        if (request.getSection() != null) profile.setSection(request.getSection());

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
            String oldValue = objectMapper.writeValueAsString(academic);

            if (request.getCgpa() != null) academic.setCgpa(request.getCgpa());
            if (request.getTenthPercentage() != null) academic.setTenthPercentage(request.getTenthPercentage());
            if (request.getTwelfthPercentage() != null) academic.setTwelfthPercentage(request.getTwelfthPercentage());
            if (request.getDiplomaPercentage() != null) academic.setDiplomaPercentage(request.getDiplomaPercentage());
            if (request.getActiveBacklogs() != null) academic.setActiveBacklogs(request.getActiveBacklogs());
            if (request.getHistoryOfBacklogs() != null) academic.setHistoryOfBacklogs(request.getHistoryOfBacklogs());

            academicRepository.save(academic);

            String newValue = objectMapper.writeValueAsString(academic);
            auditService.log("UPDATE_STUDENT_ACADEMIC", "StudentAcademic", academic.getId(),
                    oldValue, newValue);
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

    private void validateStudentAccess(StudentProfile profile) {
        Role role = securityUtils.getCurrentUserRole();
        if (role == Role.PO) return;
        if (role == Role.STUDENT) return;

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
        StudentAcademic academic = academicRepository.findByStudentProfileId(profile.getId());
        StudentProfessional professional = professionalRepository.findByStudentProfileId(profile.getId());
        StudentPlacementInfo placementInfo = placementInfoRepository.findByStudentProfileId(profile.getId());

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
                    .interviewsAttended(placementInfo.getInterviewsAttended());
        }

        return builder.build();
    }
}
