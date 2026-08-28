package com.college.placement.contact;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.contact.dto.ContactRequestResponse;
import com.college.placement.contact.dto.CreateContactRequest;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContactRequestService {

    private final ContactRequestRepository contactRequestRepository;
    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional
    public ContactRequestResponse createContactRequest(CreateContactRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != Role.STUDENT && currentUser.getRole() != Role.PR) {
            throw new ForbiddenException("Only students and PRs can create contact requests.");
        }

        StudentProfile profile = profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile"));

        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user", request.getTargetUserId()));

        if (targetUser.getRole() == Role.PO) {
            throw new ForbiddenException("Cannot contact PO directly.");
        }

        if (currentUser.getDepartment() != null && targetUser.getDepartment() != null) {
            if (!currentUser.getDepartment().getId().equals(targetUser.getDepartment().getId())) {
                throw new ForbiddenException("Can only contact users within your department.");
            }
        }

        ContactRequest contactRequest = ContactRequest.builder()
                .studentProfile(profile)
                .targetUser(targetUser)
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(ContactRequestStatus.PENDING)
                .build();

        contactRequest = contactRequestRepository.save(contactRequest);
        auditService.log("CREATE_CONTACT_REQUEST", "ContactRequest", contactRequest.getId(),
                profile.getRegisterNumber() + " -> " + targetUser.getName());

        return toResponse(contactRequest);
    }

    @Transactional(readOnly = true)
    public Page<ContactRequestResponse> getIncomingRequests(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return contactRequestRepository.findByTargetUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ContactRequestResponse> getMyRequests(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        StudentProfile profile = profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile"));
        return contactRequestRepository.findByStudentProfileUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public ContactRequestResponse updateStatus(Long id, ContactRequestStatus status) {
        User currentUser = securityUtils.getCurrentUser();
        ContactRequest request = contactRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact request", id));

        if (!request.getTargetUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You can only update requests addressed to you.");
        }

        request.setStatus(status);
        if (status != ContactRequestStatus.PENDING) {
            request.setResolvedAt(LocalDateTime.now());
        }

        request = contactRequestRepository.save(request);
        auditService.log("UPDATE_CONTACT_REQUEST", "ContactRequest", id, status.name());

        return toResponse(request);
    }

    private ContactRequestResponse toResponse(ContactRequest request) {
        return ContactRequestResponse.builder()
                .id(request.getId())
                .studentProfileId(request.getStudentProfile().getId())
                .studentName(request.getStudentProfile().getUser().getName())
                .registerNumber(request.getStudentProfile().getRegisterNumber())
                .departmentName(request.getStudentProfile().getUser().getDepartment() != null ?
                    request.getStudentProfile().getUser().getDepartment().getName() : null)
                .targetUserId(request.getTargetUser().getId())
                .targetUserName(request.getTargetUser().getName())
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt().toString())
                .resolvedAt(request.getResolvedAt() != null ? request.getResolvedAt().toString() : null)
                .build();
    }
}
