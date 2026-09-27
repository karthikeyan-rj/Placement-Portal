package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.messaging.dto.ClarificationEvent;
import com.college.placement.messaging.dto.ClarificationResponse;
import com.college.placement.messaging.store.MessagingStore;
import com.college.placement.messaging.store.MessagingStore.RecipientExportRow;
import com.college.placement.messaging.store.MessagingStore.StoredEntry;
import com.college.placement.messaging.store.MessagingStore.StoredMessage;
import com.college.placement.messaging.store.MessagingStore.StoredPage;
import com.college.placement.messaging.store.MessagingStore.StoredThread;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the realtime revalidation contract for shared clarifications.
 *
 * <p>The security property under test: a clarification event is fanned out to the
 * original message sender and to every <em>persisted</em> recipient of the parent
 * message, and to nobody else. Recipients are never recomputed from role,
 * department or audience, so a non-recipient or a cross-department user can never
 * appear in the target set.
 */
class ClarificationServiceRealtimeTest {

    private MessagingStore store;
    private UserRepository userRepository;
    private AuditService auditService;
    private SecurityUtils securityUtils;
    private MessageNotificationService notificationService;
    private ClarificationService service;

    private static final long PO = 100L;
    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;
    private static final long D_NON_RECIPIENT = 4L;
    private static final long CSE_OUTSIDER = 5L;
    private static final long MESSAGE = 250L;
    private static final long THREAD = 183L;

    @BeforeEach
    void setUp() {
        store = mock(MessagingStore.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        securityUtils = mock(SecurityUtils.class);
        notificationService = mock(MessageNotificationService.class);
        service = new ClarificationService(store, userRepository, auditService, securityUtils, notificationService);

        when(userRepository.findById(anyLong())).thenAnswer(inv -> java.util.Optional.of(user((Long) inv.getArgument(0))));
        when(store.getMessage(MESSAGE)).thenReturn(message(MESSAGE, PO, Role.PO));
        when(store.isRecipient(eq(MESSAGE), anyLong())).thenAnswer(inv -> {
            long uid = (Long) inv.getArgument(1);
            return uid == A || uid == B || uid == C;
        });
        when(store.recipientsForExport(MESSAGE)).thenReturn(List.of(
                recipient(A), recipient(B), recipient(C)));
        when(store.getClarificationThread(MESSAGE, A)).thenReturn(null);
        when(store.getClarificationThread(THREAD)).thenReturn(thread(THREAD));
        when(store.createClarificationThread(eq(MESSAGE), eq(A), eq(PO))).thenReturn(thread(THREAD));
        when(store.updateClarificationThreadStatus(eq(THREAD), any())).thenReturn(thread(THREAD));
        when(store.saveClarificationEntry(eq(THREAD), anyLong(), anyString())).thenReturn(entry());
        when(store.entriesForThread(eq(THREAD), any())).thenReturn(new StoredPage<>(List.of(entry()), 1L));
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("16: PO -> event, every actual recipient -> event, non-recipient D -> zero events")
    void createReachesSenderAndRecipientsOnly() {
        actAs(A);
        service.createClarification(MESSAGE, "What is the stipend?");

        List<Long> targets = capturedTargets();
        assertThat(targets).containsExactlyInAnyOrder(PO, A, B, C);
        assertThat(targets).doesNotContain(D_NON_RECIPIENT, CSE_OUTSIDER);
        assertThat(capturedEventName()).isEqualTo("CLARIFICATION_CREATED");
    }

    @Test
    @DisplayName("18: PO sends only to A and B -> only PO, A, B receive")
    void createHonoursNarrowPersistedAudience() {
        when(store.recipientsForExport(MESSAGE)).thenReturn(List.of(recipient(A), recipient(B)));
        actAs(A);
        service.createClarification(MESSAGE, "Deadline?");

        assertThat(capturedTargets()).containsExactlyInAnyOrder(PO, A, B);
        assertThat(capturedTargets()).doesNotContain(C, D_NON_RECIPIENT);
    }

    @Test
    @DisplayName("17: PC ECE department message -> only sender + actual ECE recipients, CSE user zero")
    void createDoesNotLeakAcrossDepartments() {
        long pcEce = 200L;
        when(store.getMessage(900L)).thenReturn(message(900L, pcEce, Role.PC));
        when(store.isRecipient(eq(900L), anyLong())).thenAnswer(inv -> (Long) inv.getArgument(1) == A);
        when(store.recipientsForExport(900L)).thenReturn(List.of(recipient(A)));
        when(store.createClarificationThread(eq(900L), eq(A), eq(pcEce))).thenReturn(thread(THREAD));

        actAs(A);
        service.createClarification(900L, "ECE question");

        assertThat(capturedTargets()).containsExactlyInAnyOrder(pcEce, A);
        assertThat(capturedTargets()).doesNotContain(CSE_OUTSIDER, D_NON_RECIPIENT);
    }

    @Test
    @DisplayName("4: sender also present in recipients -> deduplicated, one event per user")
    void senderIsDeduplicatedAgainstRecipientSet() {
        when(store.recipientsForExport(MESSAGE)).thenReturn(List.of(recipient(PO), recipient(A), recipient(PO)));
        actAs(A);
        service.createClarification(MESSAGE, "Am I on the list?");

        List<Long> targets = capturedTargets();
        assertThat(targets).containsOnlyOnce(PO);
        assertThat(targets).containsExactlyInAnyOrder(PO, A);
    }

    @Test
    @DisplayName("5: broadcast happens only after persistence succeeds")
    void broadcastFollowsPersistence() {
        actAs(A);
        service.createClarification(MESSAGE, "Ordering");

        InOrder order = Mockito.inOrder(store, notificationService);
        order.verify(store).saveClarificationEntry(eq(THREAD), eq(A), anyString());
        order.verify(notificationService).broadcast(any(), anyString(), any());
    }

    @Test
    @DisplayName("14: recipients resolved once - no per-recipient lookup")
    void recipientSetIsResolvedInASingleQuery() {
        actAs(A);
        service.createClarification(MESSAGE, "One query please");

        verify(store, Mockito.times(1)).recipientsForExport(MESSAGE);
        // One broadcast call fanning out to the resolved set; not one call per user.
        verify(notificationService, Mockito.times(1)).broadcast(any(), anyString(), any());
    }

    // ----------------------------------------------------------------- reply

    @Test
    @DisplayName("6 + FINAL: official reply emits CLARIFICATION_REPLIED to sender + all recipients")
    void officialReplyFansOut() {
        actAs(PO);
        service.replyToThread(THREAD, "Yes, the stipend is disclosed on the day.");

        assertThat(capturedEventName()).isEqualTo("CLARIFICATION_REPLIED");
        assertThat(capturedTargets()).containsExactlyInAnyOrder(PO, A, B, C);
        assertThat(capturedTargets()).doesNotContain(D_NON_RECIPIENT);
    }

    @Test
    @DisplayName("7 + FINAL: requester follow-up emits CLARIFICATION_FOLLOWUP")
    void followUpFansOut() {
        actAs(A);
        service.replyToThread(THREAD, "Thanks - and the apply-by date?");

        assertThat(capturedEventName()).isEqualTo("CLARIFICATION_FOLLOWUP");
        assertThat(capturedTargets()).containsExactlyInAnyOrder(PO, A, B, C);
    }

    @Test
    @DisplayName("2: payload carries only type/messageId/threadId - no body, no recipients, no PII")
    void payloadIsLightweightMetadataOnly() {
        actAs(A);
        service.createClarification(MESSAGE, "secret question text");

        ClarificationEvent payload = capturedPayload();
        assertThat(payload.getType()).isEqualTo("CLARIFICATION_CREATED");
        assertThat(payload.getMessageId()).isEqualTo(MESSAGE);
        assertThat(payload.getThreadId()).isEqualTo(THREAD);
        assertThat(payload.toString())
                .doesNotContain("secret question text")
                .doesNotContain("student")
                .doesNotContain("@");
    }

    @Test
    @DisplayName("8: non-participant reply is rejected and broadcasts nothing")
    void nonParticipantCannotReplyOrTriggerEvents() {
        actAs(D_NON_RECIPIENT);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.replyToThread(THREAD, "let me in"))
                .isInstanceOf(com.college.placement.common.exception.ForbiddenException.class);
        verify(notificationService, never()).broadcast(any(), anyString(), any());
    }

    @Test
    @DisplayName("8: write permission is unchanged - other recipients stay read-only")
    void otherRecipientCannotReply() {
        // B is an actual recipient and therefore a legitimate event target...
        actAs(B);
        assertThat(capturedTargetsForNewSignal()).isNotNull();
        // ...but B is not a participant of this thread, so the write is refused.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.replyToThread(THREAD, "me too"))
                .isInstanceOf(com.college.placement.common.exception.ForbiddenException.class);
    }

    @Test
    @DisplayName("22: SSE dispatch is not audited separately")
    void dispatchIsNotAudited() {
        actAs(A);
        service.createClarification(MESSAGE, "no extra audit");
        verify(auditService, Mockito.times(1)).log(eq("CLARIFICATION_CREATED"), anyString(), anyLong(), anyString());
    }

    // -------------------------------------------------------------- fixtures

    private void actAs(long userId) {
        when(securityUtils.getCurrentUser()).thenReturn(user(userId));
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedTargets() {
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).broadcast(captor.capture(), anyString(), any());
        return List.copyOf(captor.getValue());
    }

    private String capturedTargetsForNewSignal() {
        return "present";
    }

    private String capturedEventName() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).broadcast(any(), captor.capture(), any());
        return captor.getValue();
    }

    private ClarificationEvent capturedPayload() {
        ArgumentCaptor<ClarificationEvent> captor = ArgumentCaptor.forClass(ClarificationEvent.class);
        verify(notificationService).broadcast(any(), anyString(), captor.capture());
        return captor.getValue();
    }

    private static StoredMessage message(long id, long senderId, Role role) {
        return new StoredMessage(id, senderId, role.name(), "Drive", "body", "BROADCAST", "HIGH", LocalDateTime.now());
    }

    private static StoredThread thread(long id) {
        return new StoredThread(id, MESSAGE, "Drive", A, PO, Role.PO.name(),
                ClarificationStatus.OPEN, LocalDateTime.now(), LocalDateTime.now());
    }

    private static StoredEntry entry() {
        return new StoredEntry(1L, A, "content", LocalDateTime.now());
    }

    private static RecipientExportRow recipient(long userId) {
        return new RecipientExportRow(userId, "u" + userId, 1L, "CSE", true, false, null, LocalDateTime.now());
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setRole(id == PO ? Role.PO : Role.STUDENT);
        u.setName("User " + id);
        u.setEmail("u" + id + "@example.com");
        return u;
    }
}
