package com.college.placement.placement;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.InterviewStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.placement.dto.CreateInterviewRequest;
import com.college.placement.placement.dto.StudentInterviewResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentPlacementInfo;
import com.college.placement.student.StudentPlacementInfoRepository;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentInterviewService {

    private final StudentInterviewRepository interviewRepository;
    private final PlacementRecordRepository placementRecordRepository;
    private final PlacementDriveRepository driveRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentPlacementInfoRepository placementInfoRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<StudentInterviewResponse> getInterviewsByStudent(Long studentProfileId) {
        return interviewRepository.findByStudentProfileIdOrderByInterviewDateDesc(studentProfileId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentInterviewResponse> getInterviewsByDrive(Long driveId) {
        securityUtils.requireAnyRole(Role.PO, Role.PC);
        return interviewRepository.findByPlacementDriveIdOrderByInterviewDateDesc(driveId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<StudentInterviewResponse> getMyInterviews(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return interviewRepository.findByStudentProfileUserIdOrderByInterviewDateDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public StudentInterviewResponse recordInterview(CreateInterviewRequest request) {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        StudentProfile profile = profileRepository.findById(request.getStudentProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile", request.getStudentProfileId()));

        PlacementDrive drive = driveRepository.findById(request.getPlacementDriveId())
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", request.getPlacementDriveId()));

        InterviewStatus status = InterviewStatus.SCHEDULED;
        if (request.getStatus() != null) {
            try {
                status = InterviewStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid interview status: " + request.getStatus());
            }
        }

        StudentInterview interview = StudentInterview.builder()
                .studentProfile(profile)
                .placementDrive(drive)
                .roundName(request.getRoundName())
                .status(status)
                .attended(request.getAttended() != null ? request.getAttended() : false)
                .remarks(request.getRemarks())
                .interviewDate(request.getInterviewDate() != null ? LocalDate.parse(request.getInterviewDate()) : null)
                .build();

        interview = interviewRepository.save(interview);

        if (status == InterviewStatus.SELECTED) {
            StudentPlacementInfo placementInfo = placementInfoRepository.findByStudentProfileId(profile.getId());
            if (placementInfo == null) {
                placementInfo = StudentPlacementInfo.builder().studentProfile(profile).build();
            }
            placementInfo.setPlacementStatus(com.college.placement.common.enums.PlacementStatus.PLACED);
            placementInfo.setPlacedCompany(drive.getCompany());
            placementInfo.setInterviewsAttended(placementInfo.getInterviewsAttended() + 1);
            placementInfoRepository.save(placementInfo);

            PlacementRecord record = PlacementRecord.builder()
                    .studentProfile(profile)
                    .company(drive.getCompany())
                    .placementDrive(drive)
                    .packageLpa(drive.getPackageLpa())
                    .placementDate(LocalDate.now())
                    .status("PLACED")
                    .build();
            placementRecordRepository.save(record);
        } else {
            StudentPlacementInfo placementInfo = placementInfoRepository.findByStudentProfileId(profile.getId());
            if (placementInfo != null) {
                placementInfo.setInterviewsAttended(placementInfo.getInterviewsAttended() + 1);
                placementInfoRepository.save(placementInfo);
            }
        }

        auditService.log("RECORD_INTERVIEW", "StudentInterview", interview.getId(),
                profile.getRegisterNumber() + " - " + drive.getJobRole() + " - " + interview.getRoundName());

        return toResponse(interview);
    }

    private StudentInterviewResponse toResponse(StudentInterview interview) {
        return StudentInterviewResponse.builder()
                .id(interview.getId())
                .studentProfileId(interview.getStudentProfile().getId())
                .studentName(interview.getStudentProfile().getUser().getName())
                .registerNumber(interview.getStudentProfile().getRegisterNumber())
                .placementDriveId(interview.getPlacementDrive().getId())
                .driveJobRole(interview.getPlacementDrive().getJobRole())
                .companyName(interview.getPlacementDrive().getCompany().getName())
                .roundName(interview.getRoundName())
                .status(interview.getStatus().name())
                .attended(interview.getAttended())
                .remarks(interview.getRemarks())
                .interviewDate(interview.getInterviewDate() != null ? interview.getInterviewDate().toString() : null)
                .build();
    }
}
