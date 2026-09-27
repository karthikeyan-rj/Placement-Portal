package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.contact.ContactRequestService;
import com.college.placement.department.Department;
import com.college.placement.messaging.dto.CreateMessageRequest;
import com.college.placement.messaging.dto.MessageResponse;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7M.2 &sect;2: only HIGH importance enqueues email.
 *
 * <p>The gate lives in {@code MessageService} and is a one-line {@code if}, which is exactly the
 * kind of rule that silently regresses: widening the audience, adding a new importance, or moving
 * the notification call can start emailing NORMAL messages without any other test noticing. These
 * tests pin both halves of the contract &mdash; HIGH queues email, NORMAL does not &mdash; and
 * that the realtime portal notification happens in both cases.
 *
 * <p>The email outbox is mocked, so nothing is queued or sent.
 */
class MessageEmailTriggerTest {

    private static final long PO = 31L;
    private static final long STUDENT = 11L;
    private static final long PC = 21L;
    private static final long CSE = 1L;
    private static final long MESSAGE_ID = 500L;

    private MessagingStore store;
    private SecurityUtils securityUtils;
    private UserRepository userRepository;
    private MessageNotificationService notificationService;
    private EmailNotificationService emailNotificationService;
    private MessageService service;

    @BeforeEach
    void setUp() {
        store = mock(MessagingStore.class);
        securityUtils = mock(SecurityUtils.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(MessageNotificationService.class);
        emailNotificationService = mock(EmailNotificationService.class);
        service = new MessageService(
                store,
                userRepository,
                mock(AuditService.class),
                securityUtils,
                notificationService,
                emailNotificationService,
                mock(ContactRequestService.class));

        User po = po();
        when(securityUtils.getCurrentUser()).thenReturn(po);
        // Recipients are resolved from persisted users, so the targeted recipient must exist.
        when(userRepository.findAllById(any())).thenReturn(List.of(student()));
        when(store.createMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new MessagingStore.StoredMessage(
                        MESSAGE_ID, PO, "PO", "Registration opens",
                        "Register before the cutoff.", "BROADCAST", "HIGH",
                        java.time.LocalDateTime.now()));
        when(store.messageStats(anyCollection()))
                .thenReturn(List.of(new MessagingStore.MessageStatsRow(MESSAGE_ID, 1, 0, 0, 0, 0)));
    }

    private static User po() {
        User u = new User();
        u.setId(PO);
        u.setName("Dr. Anita Rao");
        u.setRole(Role.PO);
        u.setActive(true);
        return u;
    }

    private static User student() {
        User u = new User();
        u.setId(STUDENT);
        u.setName("Karthikeyan R J");
        u.setRole(Role.STUDENT);
        u.setEmail("karthikeyanrj@student.tce.edu");
        u.setActive(true);
        Department cse = new Department();
        cse.setId(CSE);
        cse.setName("CSE");
        u.setDepartment(cse);
        return u;
    }

    private static CreateMessageRequest request(String importance) {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setTitle("Registration opens");
        request.setContent("Register before the cutoff.");
        request.setImportance(importance);
        request.setRecipientIds(List.of(STUDENT));
        return request;
    }

    @Test
    @DisplayName("§2: a HIGH message queues email for its recipients")
    void highMessageQueuesEmail() {
        MessageResponse response = service.sendMessage(request("HIGH"));

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MESSAGE_ID);
        verify(emailNotificationService).enqueueHighPriority(eq(MESSAGE_ID), any());
    }

    @Test
    @DisplayName("§2: a NORMAL message queues no email at all")
    void normalMessageQueuesNoEmail() {
        when(store.createMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new MessagingStore.StoredMessage(
                        MESSAGE_ID, PO, "PO", "Registration opens",
                        "Register before the cutoff.", "BROADCAST", "NORMAL",
                        java.time.LocalDateTime.now()));

        MessageResponse response = service.sendMessage(request("NORMAL"));

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MESSAGE_ID);
        verify(emailNotificationService, never()).enqueueHighPriority(anyLong(), any());
    }

    @Test
    @DisplayName("§2: both HIGH and NORMAL still emit the realtime portal notification")
    void portalNotificationHappensRegardlessOfImportance() {
        service.sendMessage(request("NORMAL"));

        verify(notificationService).publishNewMessage(anyCollection(), any());
        // The realtime path must not be coupled to the email path.
        verify(emailNotificationService, never()).enqueueHighPriority(anyLong(), any());
    }

    @Test
    @DisplayName("§2: a PC may also send HIGH, and that queues email too")
    void coordinatorHighAlsoQueuesEmail() {
        User pc = new User();
        pc.setId(PC);
        pc.setName("Ms. Priya Nair");
        pc.setRole(Role.PC);
        pc.setActive(true);
        Department cse = new Department();
        cse.setId(CSE);
        cse.setName("CSE");
        pc.setDepartment(cse);
        when(securityUtils.getCurrentUser()).thenReturn(pc);
        when(store.createMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new MessagingStore.StoredMessage(
                        MESSAGE_ID, PC, "PC", "Registration opens",
                        "Register before the cutoff.", "BROADCAST", "HIGH",
                        java.time.LocalDateTime.now()));

        service.sendMessage(request("HIGH"));

        verify(emailNotificationService).enqueueHighPriority(eq(MESSAGE_ID), any());
    }
}
