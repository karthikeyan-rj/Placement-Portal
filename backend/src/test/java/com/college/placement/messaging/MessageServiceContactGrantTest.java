package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.contact.ContactRequestService;
import com.college.placement.department.Department;
import com.college.placement.messaging.dto.CreateMessageRequest;
import com.college.placement.messaging.email.EmailNotificationService;
import com.college.placement.messaging.store.MessagingStore;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7P.4: the contact-request exception inside direct-message
 * authorization.
 *
 * <p>The rule under test is the ordering required by the phase: the existing
 * role/department logic stays the first source of truth, and the contact
 * request is only consulted when that logic denies the pair. PO must stay
 * unreachable no matter what, and audience sends must never inherit the
 * exception.
 */
class MessageServiceContactGrantTest {

    private UserRepository userRepository;
    private SecurityUtils securityUtils;
    private ContactRequestService contactRequestService;
    private MessageService service;

    private static final long STUDENT = 11L;
    private static final long PR = 12L;
    private static final long PC = 21L;
    private static final long OTHER_STUDENT = 13L;
    private static final long PO = 31L;
    private static final long CSE = 1L;
    private static final long ECE = 2L;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        securityUtils = mock(SecurityUtils.class);
        contactRequestService = mock(ContactRequestService.class);
        service = new MessageService(
                mock(MessagingStore.class),
                userRepository,
                mock(AuditService.class),
                securityUtils,
                mock(MessageNotificationService.class),
                mock(EmailNotificationService.class),
                contactRequestService);

        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (!(arg instanceof List<?> ids)) return List.of();
            return ids.stream().map(id -> known((Long) id)).filter(java.util.Objects::nonNull).toList();
        });
        when(userRepository.findByDepartmentId(anyLong())).thenReturn(List.of(user(PC, Role.PC, CSE)));
        when(contactRequestService.hasDirectMessagingPermission(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    @DisplayName("32: an existing rule that already allows the pair short-circuits the contact request")
    void existingRuleWins() {
        actAs(user(STUDENT, Role.STUDENT, CSE));
        assertThat(service.countRecipients(directed(PC))).isEqualTo(1);
        verify(contactRequestService, never()).hasDirectMessagingPermission(anyLong(), anyLong());
    }

    @Test
    @DisplayName("32: after acceptance a PR may reach the coordinator their request was accepted for")
    void acceptedRequestUnlocksRequester() {
        actAs(user(PR, Role.PR, CSE));
        when(contactRequestService.hasDirectMessagingPermission(PR, PC)).thenReturn(true);

        assertThat(service.countRecipients(directed(PC))).isEqualTo(1);
    }

    @Test
    @DisplayName("7/32: the reverse direction is unlocked too")
    void acceptedRequestUnlocksCoordinator() {
        actAs(user(PC, Role.PC, ECE));
        when(contactRequestService.hasDirectMessagingPermission(PC, PR)).thenReturn(true);

        assertThat(service.countRecipients(directed(PR))).isEqualTo(1);
    }

    @Test
    @DisplayName("7/32: a cross-department pair with no accepted request is still blocked")
    void unrelatedPairBlocked() {
        actAs(user(PC, Role.PC, ECE));
        assertThat(service.countRecipients(directed(OTHER_STUDENT))).isZero();
    }

    @Test
    @DisplayName("9/32: PO stays blocked as a recipient even if the permission check claims otherwise")
    void poIsNeverUnlocked() {
        actAs(user(STUDENT, Role.STUDENT, CSE));
        when(contactRequestService.hasDirectMessagingPermission(anyLong(), anyLong())).thenReturn(true);
        assertThat(service.countRecipients(directed(PO))).isZero();

        actAs(user(PR, Role.PR, CSE));
        assertThat(service.countRecipients(directed(PO))).isZero();
    }

    @Test
    @DisplayName("9: the pre-existing rule for a PO sender is left untouched")
    void poSenderRuleUnchanged() {
        actAs(user(PO, Role.PO, null));
        assertThat(service.countRecipients(directed(STUDENT))).isEqualTo(1);
    }

    @Test
    @DisplayName("32: without an accepted request the pair is still denied")
    void withoutGrantStillDenied() {
        actAs(user(PR, Role.PR, CSE));
        assertThat(service.countRecipients(directed(PC))).isZero();
    }

    @Test
    @DisplayName("8/27: a department audience never inherits the contact-request exception")
    void departmentAudienceIgnoresException() {
        actAs(user(PR, Role.PR, CSE));
        when(contactRequestService.hasDirectMessagingPermission(anyLong(), anyLong())).thenReturn(true);

        CreateMessageRequest request = new CreateMessageRequest();
        request.setTitle("t");
        request.setContent("c");
        request.setDepartmentId(CSE);
        request.setTargetRole("PC");

        assertThat(service.countRecipients(request)).isZero();
        verify(contactRequestService, never()).hasDirectMessagingPermission(anyLong(), anyLong());
    }

    @Test
    @DisplayName("8: an inactive recipient is filtered out even with a grant")
    void inactiveRecipientFiltered() {
        User inactive = user(PC, Role.PC, ECE);
        inactive.setActive(false);
        org.mockito.Mockito.doReturn(List.of(inactive)).when(userRepository).findAllById(any());
        when(contactRequestService.hasDirectMessagingPermission(PR, PC)).thenReturn(true);

        actAs(user(PR, Role.PR, CSE));
        assertThat(service.countRecipients(directed(PC))).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private void actAs(User user) {
        when(securityUtils.getCurrentUser()).thenReturn(user);
    }

    private static CreateMessageRequest directed(long... ids) {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setTitle("Title");
        request.setContent("Content");
        request.setRecipientIds(java.util.Arrays.stream(ids).boxed().toList());
        return request;
    }

    private static User known(long id) {
        // The coordinator sits in ECE while the requester side is in CSE, so the
        // two directions below genuinely depend on the contact-request grant
        // instead of the pre-existing same-department rule.
        if (id == STUDENT) return user(STUDENT, Role.STUDENT, CSE);
        if (id == OTHER_STUDENT) return user(OTHER_STUDENT, Role.STUDENT, CSE);
        if (id == PR) return user(PR, Role.PR, CSE);
        if (id == PC) return user(PC, Role.PC, ECE);
        if (id == PO) return user(PO, Role.PO, null);
        return null;
    }

    private static User user(long id, Role role, Long departmentId) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        u.setActive(true);
        if (departmentId != null) {
            Department d = new Department();
            d.setId(departmentId);
            d.setName("CSE");
            u.setDepartment(d);
        }
        return u;
    }
}
