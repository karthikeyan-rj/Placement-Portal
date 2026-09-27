package com.college.placement.contact;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.contact.dto.ContactRequestCountsResponse;
import com.college.placement.contact.dto.ContactRequestEvent;
import com.college.placement.contact.dto.ContactRequestResponse;
import com.college.placement.contact.dto.ContactRequestTargetResponse;
import com.college.placement.contact.dto.CreateContactRequest;
import com.college.placement.contact.dto.UpdateContactRequestStatus;
import com.college.placement.messaging.MessageNotificationService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContactRequestService {

    public static final String EVENT_CREATED = "CONTACT_REQUEST_CREATED";
    public static final String EVENT_ACCEPTED = "CONTACT_REQUEST_ACCEPTED";
    public static final String EVENT_REJECTED = "CONTACT_REQUEST_REJECTED";
    public static final String EVENT_RESOLVED = "CONTACT_REQUEST_RESOLVED";

    private static final Set<Role> REQUESTER_ROLES = EnumSet.of(Role.STUDENT, Role.PR);
    private static final Set<ContactRequestStatus> MESSAGING_GRANTING_STATUSES =
            EnumSet.of(ContactRequestStatus.ACCEPTED, ContactRequestStatus.RESOLVED);
    private static final Set<ContactRequestStatus> TERMINAL_STATUSES =
            EnumSet.of(ContactRequestStatus.REJECTED, ContactRequestStatus.RESOLVED);

    /**
     * The only permitted moves. PENDING may be accepted or declined, an
     * accepted case may be closed out, and both REJECTED and RESOLVED are
     * terminal: a request can never be reopened or reversed.
     */
    private static final Map<ContactRequestStatus, Set<ContactRequestStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private final ContactRequestRepository contactRequestRepository;
    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final MessageNotificationService messageNotificationService;

    private static Map<ContactRequestStatus, Set<ContactRequestStatus>> buildTransitions() {
        Map<ContactRequestStatus, Set<ContactRequestStatus>> transitions = new LinkedHashMap<>();
        transitions.put(ContactRequestStatus.PENDING,
                EnumSet.of(ContactRequestStatus.ACCEPTED, ContactRequestStatus.REJECTED));
        transitions.put(ContactRequestStatus.ACCEPTED, EnumSet.of(ContactRequestStatus.RESOLVED));
        transitions.put(ContactRequestStatus.REJECTED, EnumSet.noneOf(ContactRequestStatus.class));
        transitions.put(ContactRequestStatus.RESOLVED, EnumSet.noneOf(ContactRequestStatus.class));
        return Map.copyOf(transitions);
    }

    @Transactional
    public ContactRequestResponse createContactRequest(CreateContactRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        if (!REQUESTER_ROLES.contains(currentUser.getRole())) {
            throw new ForbiddenException("Only students and PRs can create contact requests.");
        }

        StudentProfile profile = profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile"));

        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user", request.getTargetUserId()));

        if (targetUser.getId().equals(currentUser.getId())) {
            throw new BadRequestException("You cannot send a contact request to yourself.");
        }
        if (targetUser.getRole() == Role.PO) {
            throw new ForbiddenException("Cannot contact PO directly.");
        }
        if (targetUser.getRole() != Role.PC) {
            throw new ForbiddenException("Contact requests can only be sent to placement coordinators.");
        }
        if (!Boolean.TRUE.equals(targetUser.getActive())) {
            throw new ConflictException("This placement coordinator is no longer available for contact requests.");
        }
        if (currentUser.getDepartment() == null || targetUser.getDepartment() == null
                || !currentUser.getDepartment().getId().equals(targetUser.getDepartment().getId())) {
            throw new ForbiddenException("Can only contact placement coordinators within your department.");
        }
        if (contactRequestRepository.existsBetween(currentUser.getId(), targetUser.getId(), ContactRequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending contact request for this coordinator.");
        }

        ContactRequest contactRequest = contactRequestRepository.save(ContactRequest.builder()
                .studentProfile(profile)
                .targetUser(targetUser)
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(ContactRequestStatus.PENDING)
                .build());

        Long requestId = contactRequest.getId();
        auditContactRequest(EVENT_CREATED, requestId, currentUser.getId(), targetUser.getId(),
                ContactRequestStatus.PENDING);

        messageNotificationService.broadcast(List.of(targetUser.getId()), EVENT_CREATED,
                new ContactRequestEvent(EVENT_CREATED, requestId));

        return toResponse(contactRequest);
    }

    @Transactional(readOnly = true)
    public Page<ContactRequestResponse> getIncomingRequests(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return contactRequestRepository.findIncomingForUser(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ContactRequestResponse> getMyRequests(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        if (!REQUESTER_ROLES.contains(currentUser.getRole())) {
            throw new ForbiddenException("Only students and PRs can send contact requests.");
        }
        StudentProfile profile = profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile"));
        return contactRequestRepository.findSentByUser(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public ContactRequestResponse updateStatus(Long id, UpdateContactRequestStatus payload) {
        User currentUser = securityUtils.getCurrentUser();
        ContactRequestStatus requestedStatus = parseStatus(payload == null ? null : payload.getStatus());

        ContactRequest request = contactRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact request", id));

        if (!request.getTargetUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You can only update requests addressed to you.");
        }

        ContactRequestStatus currentStatus = request.getStatus();
        if (currentStatus == requestedStatus) {
            // Idempotent replay of the same action (e.g. a double click): the
            // state is already what the caller asked for, so report it back
            // unchanged instead of re-running the transition.
            return toResponse(request);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(requestedStatus)) {
            throw new ConflictException("Cannot change a " + currentStatus + " contact request to " + requestedStatus + ".");
        }

        request.setStatus(requestedStatus);
        if (TERMINAL_STATUSES.contains(requestedStatus)) {
            request.setResolvedAt(LocalDateTime.now());
        }
        request = contactRequestRepository.save(request);

        Long requestId = request.getId();
        Long requesterUserId = request.getStudentProfile().getUser().getId();
        auditContactRequest(eventFor(requestedStatus), requestId, currentUser.getId(),
                request.getTargetUser().getId(), requestedStatus);

        // Only the requester is told about the outcome; the coordinator already
        // performed the action.
        messageNotificationService.broadcast(List.of(requesterUserId), eventFor(requestedStatus),
                new ContactRequestEvent(eventFor(requestedStatus), requestId));

        return toResponse(request);
    }

    /**
     * Placement coordinators the caller may request contact with, resolved
     * server-side: same department, role PC, active only. PO is never included.
     */
    @Transactional(readOnly = true)
    public List<ContactRequestTargetResponse> getEligibleTargets() {
        User currentUser = securityUtils.getCurrentUser();
        if (!REQUESTER_ROLES.contains(currentUser.getRole())) {
            throw new ForbiddenException("Only students and PRs can create contact requests.");
        }
        if (currentUser.getDepartment() == null) {
            return List.of();
        }

        List<ContactRequestTargetResponse> targets = new ArrayList<>();
        for (User pc : userRepository.findByRoleAndDepartmentId(Role.PC, currentUser.getDepartment().getId())) {
            if (!Boolean.TRUE.equals(pc.getActive()) || pc.getId().equals(currentUser.getId())) {
                continue;
            }
            targets.add(ContactRequestTargetResponse.builder()
                    .id(pc.getId())
                    .name(pc.getName())
                    .role(pc.getRole().name())
                    .departmentName(pc.getDepartment() != null ? pc.getDepartment().getName() : null)
                    .build());
        }
        targets.sort(Comparator.comparing(ContactRequestTargetResponse::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return targets;
    }

    @Transactional(readOnly = true)
    public ContactRequestCountsResponse getIncomingCounts() {
        return toCounts(contactRequestRepository.countIncomingGroupedByStatus(securityUtils.getCurrentUser().getId()));
    }

    @Transactional(readOnly = true)
    public ContactRequestCountsResponse getSentCounts() {
        User currentUser = securityUtils.getCurrentUser();
        if (!REQUESTER_ROLES.contains(currentUser.getRole())) {
            throw new ForbiddenException("Only students and PRs can send contact requests.");
        }
        profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile"));
        return toCounts(contactRequestRepository.countSentGroupedByStatus(currentUser.getId()));
    }

    /**
     * Central rule for the contact-request direct-messaging exception - the one
     * place that decides whether a pair may message each other because of a
     * contact request.
     *
     * <p>Returns true only when a matching request exists whose status is
     * ACCEPTED or RESOLVED and which was a supported STUDENT/PR requester
     * against a PC target in the same department. All of those invariants -
     * including the department check and the PO exclusion - are enforced by the
     * single query in {@link ContactRequestRepository#existsMessagingGrantBetween}
     * rather than being re-derived here, so a malformed or legacy row cannot
     * grant anything the create path would have refused.
     *
     * <p>The order of the two ids does not matter: one request authorises both
     * participants, so requester to PC and PC to requester are covered without a
     * second request. PENDING and REJECTED never grant access.
     */
    @Transactional(readOnly = true)
    public boolean hasDirectMessagingPermission(Long requesterUserId, Long targetUserId) {
        if (requesterUserId == null || targetUserId == null || requesterUserId.equals(targetUserId)) {
            return false;
        }
        return contactRequestRepository.existsMessagingGrantBetween(requesterUserId, targetUserId,
                MESSAGING_GRANTING_STATUSES, REQUESTER_ROLES, Role.PC);
    }

    private ContactRequestCountsResponse toCounts(List<Object[]> grouped) {
        Map<ContactRequestStatus, Long> byStatus = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : grouped) {
            ContactRequestStatus status = (ContactRequestStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }
        return ContactRequestCountsResponse.builder()
                .pending(byStatus.getOrDefault(ContactRequestStatus.PENDING, 0L))
                .accepted(byStatus.getOrDefault(ContactRequestStatus.ACCEPTED, 0L))
                .rejected(byStatus.getOrDefault(ContactRequestStatus.REJECTED, 0L))
                .resolved(byStatus.getOrDefault(ContactRequestStatus.RESOLVED, 0L))
                .total(total)
                .build();
    }

    private ContactRequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Status is required.");
        }
        try {
            return ContactRequestStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid contact request status: " + raw.trim());
        }
    }

    private String eventFor(ContactRequestStatus status) {
        return switch (status) {
            case ACCEPTED -> EVENT_ACCEPTED;
            case REJECTED -> EVENT_REJECTED;
            case RESOLVED -> EVENT_RESOLVED;
            case PENDING -> EVENT_CREATED;
        };
    }

    /**
     * Audit metadata is limited to ids and the resulting status so that request
     * subjects and message bodies are never copied into the audit trail.
     */
    private void auditContactRequest(String action, Long requestId, Long actorId, Long targetId,
                                     ContactRequestStatus newStatus) {
        auditService.log(action, "ContactRequest", requestId,
                "actor=" + actorId + ", target=" + targetId + ", status=" + newStatus.name());
    }

    private ContactRequestResponse toResponse(ContactRequest request) {
        return ContactRequestResponse.builder()
                .id(request.getId())
                .studentProfileId(request.getStudentProfile().getId())
                .requesterUserId(request.getStudentProfile().getUser().getId())
                .studentName(request.getStudentProfile().getUser().getName())
                .registerNumber(request.getStudentProfile().getRegisterNumber())
                .departmentName(request.getStudentProfile().getUser().getDepartment() != null ?
                    request.getStudentProfile().getUser().getDepartment().getName() : null)
                .targetUserId(request.getTargetUser().getId())
                .targetUserName(request.getTargetUser().getName())
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt() != null ? request.getCreatedAt().toString() : null)
                .resolvedAt(request.getResolvedAt() != null ? request.getResolvedAt().toString() : null)
                .build();
    }
}
