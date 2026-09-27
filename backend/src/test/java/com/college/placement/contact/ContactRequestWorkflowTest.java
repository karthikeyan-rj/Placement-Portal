package com.college.placement.contact;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.contact.dto.ContactRequestEvent;
import com.college.placement.contact.dto.CreateContactRequest;
import com.college.placement.contact.dto.UpdateContactRequestStatus;
import com.college.placement.department.Department;
import com.college.placement.messaging.MessageNotificationService;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7P.4 hardening for the contact-request workflow.
 *
 * <p>Covers the create security matrix, the PENDING -> ACCEPTED/REJECTED and
 * ACCEPTED -> RESOLVED state machine (including double-click safety), the
 * metadata-only realtime contract, and the central direct-messaging permission
 * check.
 */
class ContactRequestWorkflowTest {

    private ContactRequestRepository repository;
    private StudentProfileRepository profileRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private SecurityUtils securityUtils;
    private MessageNotificationService notificationService;
    private ContactRequestService service;

    private static final long STUDENT_CSE = 11L;
    private static final long PR_CSE = 12L;
    private static final long PC_CSE = 21L;
    private static final long PC_ECE = 22L;
    private static final long PC_CSE_INACTIVE = 23L;
    private static final long PO = 31L;
    private static final long STUDENT_ECE = 41L;
    private static final long PC_UNRELATED = 42L;

    private static final long CSE = 1L;
    private static final long ECE = 2L;

    @BeforeEach
    void setUp() {
        repository = mock(ContactRequestRepository.class);
        profileRepository = mock(StudentProfileRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        securityUtils = mock(SecurityUtils.class);
        notificationService = mock(MessageNotificationService.class);
        service = new ContactRequestService(repository, profileRepository, userRepository, auditService,
                securityUtils, notificationService);

        when(repository.save(any(ContactRequest.class))).thenAnswer(inv -> {
            ContactRequest cr = inv.getArgument(0);
            if (cr.getId() == null) cr.setId(900L);
            if (cr.getCreatedAt() == null) cr.setCreatedAt(LocalDateTime.now().minusMinutes(20));
            return cr;
        });
        when(repository.existsBetween(anyLong(), anyLong(), any(ContactRequestStatus.class))).thenReturn(false);
    }

    // ------------------------------------------------------------ create matrix

    @Test
    @DisplayName("30: STUDENT -> own-department PC create is allowed")
    void studentCanRequestOwnDepartmentPc() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        User pc = user(PC_CSE, Role.PC, CSE);
        when(userRepository.findById(PC_CSE)).thenReturn(Optional.of(pc));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        assertThat(service.createContactRequest(create(PC_CSE)).getStatus()).isEqualTo("PENDING");
        verify(repository).save(any(ContactRequest.class));
    }

    @Test
    @DisplayName("30: PR -> valid PC create is allowed")
    void prCanRequestValidPc() {
        actAs(user(PR_CSE, Role.PR, CSE));
        when(userRepository.findById(PC_CSE)).thenReturn(Optional.of(user(PC_CSE, Role.PC, CSE)));
        when(profileRepository.findByUserId(PR_CSE)).thenReturn(Optional.of(profile(PR_CSE, Role.PR)));

        assertThat(service.createContactRequest(create(PC_CSE)).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("30: cross-department target is blocked")
    void crossDepartmentBlocked() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PC_ECE)).thenReturn(Optional.of(user(PC_ECE, Role.PC, ECE)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        assertThatThrownBy(() -> service.createContactRequest(create(PC_ECE)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("30/9: PO target is blocked for student and PR")
    void poTargetBlocked() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PO)).thenReturn(Optional.of(user(PO, Role.PO, null)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));
        assertThatThrownBy(() -> service.createContactRequest(create(PO)))
                .isInstanceOf(ForbiddenException.class);

        actAs(user(PR_CSE, Role.PR, CSE));
        when(profileRepository.findByUserId(PR_CSE)).thenReturn(Optional.of(profile(PR_CSE, Role.PR)));
        assertThatThrownBy(() -> service.createContactRequest(create(PO)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("1/30: inactive target is blocked and nothing is created")
    void inactiveTargetBlocked() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        User inactive = user(PC_CSE_INACTIVE, Role.PC, CSE);
        inactive.setActive(false);
        when(userRepository.findById(PC_CSE_INACTIVE)).thenReturn(Optional.of(inactive));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        assertThatThrownBy(() -> service.createContactRequest(create(PC_CSE_INACTIVE)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("2/30: a second PENDING request for the same pair is a conflict")
    void duplicatePendingBlocked() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PC_CSE)).thenReturn(Optional.of(user(PC_CSE, Role.PC, CSE)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));
        when(repository.existsBetween(STUDENT_CSE, PC_CSE, ContactRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.createContactRequest(create(PC_CSE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already have a pending contact request");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("8: a coordinator may not create a contact request")
    void coordinatorCannotCreate() {
        actAs(user(PC_CSE, Role.PC, CSE));
        assertThatThrownBy(() -> service.createContactRequest(create(PC_ECE)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("8: only coordinators are valid targets")
    void nonCoordinatorTargetBlocked() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PR_CSE)).thenReturn(Optional.of(user(PR_CSE, Role.PR, CSE)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        assertThatThrownBy(() -> service.createContactRequest(create(PR_CSE)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("8: self-addressed request is rejected")
    void selfRequestBlocked() {
        actAs(user(PC_CSE, Role.PC, CSE));
        assertThatThrownBy(() -> service.createContactRequest(create(PC_CSE)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------- state matrix

    @Test
    @DisplayName("31: PENDING -> ACCEPTED is allowed")
    void pendingToAccepted() {
        assertTransition(ContactRequestStatus.PENDING, ContactRequestStatus.ACCEPTED, ContactRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("31: PENDING -> REJECTED is allowed")
    void pendingToRejected() {
        assertTransition(ContactRequestStatus.PENDING, ContactRequestStatus.REJECTED, ContactRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("31: ACCEPTED -> RESOLVED is allowed and stamps resolvedAt")
    void acceptedToResolved() {
        ContactRequest cr = stored(ContactRequestStatus.ACCEPTED);
        assertThat(service.updateStatus(cr.getId(), status(ContactRequestStatus.RESOLVED)).getStatus())
                .isEqualTo("RESOLVED");
        verify(repository).save(cr);
        assertThat(cr.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("31: every backwards or illegal transition is blocked with a conflict")
    void illegalTransitionsBlocked() {
        assertBlocked(ContactRequestStatus.ACCEPTED, ContactRequestStatus.PENDING);
        assertBlocked(ContactRequestStatus.REJECTED, ContactRequestStatus.ACCEPTED);
        assertBlocked(ContactRequestStatus.REJECTED, ContactRequestStatus.PENDING);
        assertBlocked(ContactRequestStatus.RESOLVED, ContactRequestStatus.ACCEPTED);
        assertBlocked(ContactRequestStatus.RESOLVED, ContactRequestStatus.PENDING);
        assertBlocked(ContactRequestStatus.RESOLVED, ContactRequestStatus.REJECTED);
        assertBlocked(ContactRequestStatus.REJECTED, ContactRequestStatus.RESOLVED);
    }

    @Test
    @DisplayName("31: an unknown status value is a 400, not a server error")
    void invalidStatusIsBadRequest() {
        stored(ContactRequestStatus.PENDING);
        assertThatThrownBy(() -> service.updateStatus(900L, new UpdateContactRequestStatus() {{
            setStatus("NOT_A_STATUS");
        }})).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("4/31: repeating the same action is idempotent and does not mutate state")
    void doubleActionIsSafe() {
        ContactRequest cr = stored(ContactRequestStatus.PENDING);

        service.updateStatus(cr.getId(), status(ContactRequestStatus.ACCEPTED));
        assertThat(cr.getStatus()).isEqualTo(ContactRequestStatus.ACCEPTED);

        // The rapid second click replays ACCEPTED on an already accepted request.
        String again = service.updateStatus(cr.getId(), status(ContactRequestStatus.ACCEPTED)).getStatus();
        assertThat(again).isEqualTo("ACCEPTED");
        assertThat(cr.getStatus()).isEqualTo(ContactRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("4: double reject and double resolve stay safe")
    void doubleResolveIsSafe() {
        ContactRequest cr = stored(ContactRequestStatus.ACCEPTED);
        service.updateStatus(cr.getId(), status(ContactRequestStatus.RESOLVED));
        assertThat(service.updateStatus(cr.getId(), status(ContactRequestStatus.RESOLVED)).getStatus())
                .isEqualTo("RESOLVED");
        assertThat(cr.getStatus()).isEqualTo(ContactRequestStatus.RESOLVED);
    }

    @Test
    @DisplayName("31: only the addressed coordinator may change status")
    void unrelatedUserCannotUpdate() {
        ContactRequest cr = stored(ContactRequestStatus.PENDING);
        actAs(user(PC_UNRELATED, Role.PC, CSE));
        assertThatThrownBy(() -> service.updateStatus(cr.getId(), status(ContactRequestStatus.ACCEPTED)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------ messaging permission

    @Test
    @DisplayName("10: only ACCEPTED and RESOLVED grant direct messaging, in either direction")
    void permissionFollowsStatus() {
        when(repository.existsMessagingGrantBetween(anyLong(), anyLong(), anyCollection(), anyCollection(), any()))
                .thenReturn(false);

        assertThat(service.hasDirectMessagingPermission(STUDENT_CSE, PC_CSE)).isFalse();

        // The status set handed to the query is exactly ACCEPTED + RESOLVED.
        Mockito.clearInvocations(repository);
        service.hasDirectMessagingPermission(STUDENT_CSE, PC_CSE);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ContactRequestStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Role>> roles = ArgumentCaptor.forClass(Collection.class);
        verify(repository).existsMessagingGrantBetween(eq(STUDENT_CSE), eq(PC_CSE), statuses.capture(), roles.capture(), eq(Role.PC));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(ContactRequestStatus.ACCEPTED, ContactRequestStatus.RESOLVED);
        assertThat(roles.getValue()).containsExactlyInAnyOrder(Role.STUDENT, Role.PR);
        // The target role is pinned to PC so the query can never grant anything else.
        verify(repository).existsMessagingGrantBetween(anyLong(), anyLong(), anyCollection(), anyCollection(), eq(Role.PC));
    }

    @Test
    @DisplayName("10/7: a self or incomplete pair never has permission")
    void permissionRejectsSelfAndNull() {
        assertThat(service.hasDirectMessagingPermission(STUDENT_CSE, STUDENT_CSE)).isFalse();
        assertThat(service.hasDirectMessagingPermission(STUDENT_CSE, null)).isFalse();
        assertThat(service.hasDirectMessagingPermission(null, PC_CSE)).isFalse();
        verify(repository, never()).existsMessagingGrantBetween(anyLong(), anyLong(), anyCollection(), anyCollection(), any());
    }

    // ----------------------------------------------------------------- realtime

    @Test
    @DisplayName("22/24/33: create notifies only the target coordinator")
    void createBroadcastTargetsOnlyCoordinator() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PC_CSE)).thenReturn(Optional.of(user(PC_CSE, Role.PC, CSE)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        service.createContactRequest(create(PC_CSE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).broadcast(targets.capture(), eq(ContactRequestService.EVENT_CREATED), any());
        assertThat(targets.getValue()).containsExactly(PC_CSE);
    }

    @Test
    @DisplayName("23: the realtime payload carries only type and requestId")
    void payloadIsMetadataOnly() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(userRepository.findById(PC_CSE)).thenReturn(Optional.of(user(PC_CSE, Role.PC, CSE)));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));

        service.createContactRequest(create(PC_CSE));

        ArgumentCaptor<ContactRequestEvent> payload = ArgumentCaptor.forClass(ContactRequestEvent.class);
        verify(notificationService).broadcast(anyCollection(), eq(ContactRequestService.EVENT_CREATED), payload.capture());
        assertThat(payload.getValue().getType()).isEqualTo(ContactRequestService.EVENT_CREATED);
        assertThat(payload.getValue().getRequestId()).isEqualTo(900L);
        assertThat(payload.getValue().toString())
                .doesNotContain("Eligibility")
                .doesNotContain("clarification")
                .doesNotContain("@");
    }

    @Test
    @DisplayName("24/33: accept, decline and resolve notify only the requester")
    void outcomeEventsTargetRequester() {
        assertOutcomeEvent(ContactRequestStatus.PENDING, ContactRequestStatus.ACCEPTED,
                ContactRequestService.EVENT_ACCEPTED);
        assertOutcomeEvent(ContactRequestStatus.PENDING, ContactRequestStatus.REJECTED,
                ContactRequestService.EVENT_REJECTED);
        assertOutcomeEvent(ContactRequestStatus.ACCEPTED, ContactRequestStatus.RESOLVED,
                ContactRequestService.EVENT_RESOLVED);
    }

    @Test
    @DisplayName("25: no event is emitted when a transition is refused")
    void refusedTransitionEmitsNothing() {
        ContactRequest cr = stored(ContactRequestStatus.REJECTED);
        assertThatThrownBy(() -> service.updateStatus(cr.getId(), status(ContactRequestStatus.ACCEPTED)))
                .isInstanceOf(ConflictException.class);
        verify(notificationService, never()).broadcast(anyCollection(), any(), any());
    }

    // ------------------------------------------------------------------ scoping

    @Test
    @DisplayName("8: incoming and sent lists are scoped to the caller's own id")
    void listsAreScopedToCaller() {
        actAs(user(PC_UNRELATED, Role.PC, CSE));
        when(repository.findIncomingForUser(eq(PC_UNRELATED), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.getIncomingRequests(org.springframework.data.domain.PageRequest.of(0, 20));
        verify(repository).findIncomingForUser(eq(PC_UNRELATED), any());
        verify(repository, never()).findByTargetUserIdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    @DisplayName("8: the sent list is scoped to the requesting user")
    void sentListScopedToRequester() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        when(profileRepository.findByUserId(STUDENT_CSE)).thenReturn(Optional.of(profile(STUDENT_CSE, Role.STUDENT)));
        when(repository.findSentByUser(eq(STUDENT_CSE), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.getMyRequests(org.springframework.data.domain.PageRequest.of(0, 20));
        verify(repository).findSentByUser(eq(STUDENT_CSE), any());
    }

    @Test
    @DisplayName("12: a coordinator has no sent-requests view")
    void coordinatorHasNoSentView() {
        actAs(user(PC_CSE, Role.PC, CSE));
        assertThatThrownBy(() -> service.getMyRequests(org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("15: eligible targets are active same-department coordinators only")
    void eligibleTargetsExcludeInactiveAndOtherDepartments() {
        actAs(user(STUDENT_CSE, Role.STUDENT, CSE));
        User active = user(PC_CSE, Role.PC, CSE);
        User inactive = user(PC_CSE_INACTIVE, Role.PC, CSE);
        inactive.setActive(false);
        when(userRepository.findByRoleAndDepartmentId(Role.PC, CSE)).thenReturn(List.of(active, inactive));

        var targets = service.getEligibleTargets();
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getId()).isEqualTo(PC_CSE);
    }

    // ------------------------------------------------------------------ helpers

    private void assertOutcomeEvent(ContactRequestStatus from, ContactRequestStatus to, String event) {
        ContactRequest cr = stored(from);
        Mockito.clearInvocations(notificationService);
        service.updateStatus(cr.getId(), status(to));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).broadcast(targets.capture(), eq(event), any());
        assertThat(targets.getValue()).containsExactly(STUDENT_CSE);
    }

    private void assertTransition(ContactRequestStatus from, ContactRequestStatus to, ContactRequestStatus expected) {
        ContactRequest cr = stored(from);
        assertThat(service.updateStatus(cr.getId(), status(to)).getStatus()).isEqualTo(expected.name());
        assertThat(cr.getStatus()).isEqualTo(expected);
    }

    private void assertBlocked(ContactRequestStatus from, ContactRequestStatus to) {
        ContactRequest cr = stored(from);
        Mockito.clearInvocations(repository);
        assertThatThrownBy(() -> service.updateStatus(cr.getId(), status(to)))
                .isInstanceOf(ConflictException.class);
        assertThat(cr.getStatus()).isEqualTo(from);
        verify(repository, never()).save(any());
    }

    private ContactRequest stored(ContactRequestStatus status) {
        actAs(user(PC_CSE, Role.PC, CSE));
        ContactRequest cr = ContactRequest.builder()
                .id(900L)
                .studentProfile(profile(STUDENT_CSE, Role.STUDENT))
                .targetUser(user(PC_CSE, Role.PC, CSE))
                .subject("Drive eligibility")
                .message("Need help")
                .status(status)
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .build();
        when(repository.findById(900L)).thenReturn(Optional.of(cr));
        return cr;
    }

    private void actAs(User user) {
        when(securityUtils.getCurrentUser()).thenReturn(user);
    }

    private static UpdateContactRequestStatus status(ContactRequestStatus status) {
        UpdateContactRequestStatus dto = new UpdateContactRequestStatus();
        dto.setStatus(status.name());
        return dto;
    }

    private static CreateContactRequest create(long targetUserId) {
        CreateContactRequest dto = new CreateContactRequest();
        dto.setTargetUserId(targetUserId);
        dto.setSubject("Drive eligibility");
        dto.setMessage("I need clarification regarding drive eligibility.");
        return dto;
    }

    private static Department dept(long id, String name) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private static User user(long id, Role role, Long departmentId) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        u.setActive(true);
        if (departmentId != null) u.setDepartment(dept(departmentId, departmentId == CSE ? "CSE" : "ECE"));
        return u;
    }

    private static StudentProfile profile(long userId, Role role) {
        StudentProfile p = new StudentProfile();
        p.setId(userId + 500);
        p.setUser(user(userId, role, CSE));
        p.setRegisterNumber("REG" + userId);
        return p;
    }
}
