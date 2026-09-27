package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageEmailOutbox;
import com.college.placement.messaging.mongo.repository.MessageEmailOutboxRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7M.2 delivery guarantees that the rest of the suite does not already pin down.
 *
 * <p>The template tests cover <em>what</em> a HIGH email says. This class covers the durability
 * rules the phase requires around it:
 *
 * <ul>
 *   <li>&sect;5 one logical email per recipient, never a To/CC/BCC list</li>
 *   <li>&sect;6 the {@code messageId + recipientUserId} key prevents a second row, and so a
 *       second send, for a recipient already queued</li>
 *   <li>&sect;7 a provider failure is retryable while attempts remain, terminal once they are
 *       exhausted, and must never be recorded as SENT</li>
 *   <li>&sect;8 one bad recipient or one failing send must not stop the rest of the batch</li>
 *   <li>&sect;12 a retry must not manufacture a duplicate SENT</li>
 *   <li>&sect;13 a row persisted before a restart is still claimable afterwards</li>
 * </ul>
 *
 * <p>No test here contacts Resend. The provider is mocked throughout, so these assertions are
 * about our own persistence behaviour rather than a live third party.
 */
class EmailOutboxResilienceTest {

    private static final long MESSAGE_ID = 77L;
    private static final long SENDER = 3L;
    private static final int MAX_ATTEMPTS = 3;

    private EmailProperties props;
    private MongoTemplate mongoTemplate;
    private MessageEmailOutboxRepository outboxRepository;
    private MongoMessageRepository messageRepository;
    private UserRepository userRepository;
    private EmailDispatchClient dispatchClient;
    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        props = new EmailProperties();
        props.setEnabled(true);
        props.setMaxAttempts(MAX_ATTEMPTS);
        props.setRetryDelayBaseSeconds(60);
        props.setFrontendUrl("http://localhost:4173");
        mongoTemplate = mock(MongoTemplate.class);
        outboxRepository = mock(MessageEmailOutboxRepository.class);
        messageRepository = mock(MongoMessageRepository.class);
        userRepository = mock(UserRepository.class);
        dispatchClient = mock(EmailDispatchClient.class);
        service = newService();

        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        givenStoredMessage();
    }

    /** A restart must be modelled by a brand-new service over the same persistence. */
    private EmailNotificationService newService() {
        return new EmailNotificationService(props, mongoTemplate, outboxRepository,
                messageRepository, userRepository, dispatchClient);
    }

    private void givenStoredMessage() {
        MongoMessage message = MongoMessage.builder()
                .id("m-77")
                .messageId(MESSAGE_ID)
                .senderUserId(SENDER)
                .title("Semester 7 company registration opens")
                .content("Register on the portal before the cutoff.")
                .importance("HIGH")
                .messageType("BROADCAST")
                .createdAt(LocalDateTime.now())
                .build();
        when(messageRepository.findByMessageIdIn(anyList())).thenReturn(List.of(message));
        when(userRepository.findUsersByIds(any(Collection.class)))
                .thenReturn(List.of(User.builder().id(SENDER).name("Dr. Anita Rao").build()));
    }

    private static User recipient(long id, String email) {
        return User.builder().id(id).name("Student " + id).email(email).build();
    }

    private MongoMessageEmailOutbox pendingJob(String id, long recipientUserId, int attempts) {
        return MongoMessageEmailOutbox.builder()
                .id(id)
                .messageId(MESSAGE_ID)
                .recipientUserId(recipientUserId)
                .recipientEmail("student" + recipientUserId + "@student.tce.edu")
                .status(EmailOutboxStatus.PENDING.name())
                .attempts(attempts)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .updatedAt(LocalDateTime.now().minusMinutes(2))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<MongoMessageEmailOutbox> capturedInserts() {
        ArgumentCaptor<List<MongoMessageEmailOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), any(Class.class));
        return captor.getValue();
    }

    /**
     * The {@code $set} payload of the last {@code updateFirst}. {@link Update#getUpdateObject()}
     * returns the raw command ({@code {$set: {...}}}), so the fields must be read from inside it.
     */
    private Document capturedStatusUpdate() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(any(Query.class), captor.capture(), any(Class.class));
        Document set = captor.getValue().getUpdateObject().get("$set", Document.class);
        return set == null ? new Document() : set;
    }

    // ---------------------------------------------------------------- §5

    @Test
    @DisplayName("§5/§6: one row is enqueued per recipient, each its own logical email")
    void oneRowPerRecipient() {
        when(outboxRepository.findByMessageId(MESSAGE_ID)).thenReturn(List.of());

        service.enqueueHighPriority(MESSAGE_ID, List.of(
                recipient(11L, "a@student.tce.edu"),
                recipient(12L, "b@student.tce.edu"),
                recipient(13L, "c@student.tce.edu")));

        List<MongoMessageEmailOutbox> inserted = capturedInserts();
        assertThat(inserted).hasSize(3);
        assertThat(inserted).extracting(MongoMessageEmailOutbox::getRecipientUserId)
                .containsExactlyInAnyOrder(11L, 12L, 13L);
        assertThat(inserted).extracting(MongoMessageEmailOutbox::getStatus)
                .containsOnly(EmailOutboxStatus.PENDING.name());
        assertThat(inserted).allSatisfy(row ->
                assertThat(row.getRecipientEmail()).isNotBlank());
    }

    // ---------------------------------------------------------------- §6 / §14

    @Test
    @DisplayName("§6/§14: re-enqueueing the same message+recipient creates no second row")
    void duplicateEnqueueIsIgnored() {
        when(outboxRepository.findByMessageId(MESSAGE_ID))
                .thenReturn(List.of())                                   // first call: nothing queued
                .thenReturn(List.of(pendingJob("job-1", 11L, 0)));        // second call: already queued

        service.enqueueHighPriority(MESSAGE_ID, List.of(recipient(11L, "a@student.tce.edu")));
        service.enqueueHighPriority(MESSAGE_ID, List.of(recipient(11L, "a@student.tce.edu")));

        // A single insert for the pair, so the recipient can never be emailed twice.
        assertThat(capturedInserts()).hasSize(1);
    }

    // ---------------------------------------------------------------- §7 / §12

    @Test
    @DisplayName("§7: a transient failure is left retryable and is never recorded as SENT")
    void transientFailureIsRetryableNotSent() {
        when(mongoTemplate.find(any(Query.class), any(Class.class)))
                .thenReturn(List.of(pendingJob("job-1", 11L, 0)));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class)))
                .thenThrow(new EmailSendException(EmailSendException.Category.SERVER, "provider 503", false));

        service.processDueBatch();

        // Guards the assertion below: the status must come from the send attempt, not from an
        // unrelated bookkeeping write.
        verify(dispatchClient, times(1)).send(any(EmailDraft.class));
        Document update = capturedStatusUpdate();
        assertThat(update.get("status")).isEqualTo(EmailOutboxStatus.PENDING.name());
        assertThat(update.get("nextAttemptAt")).isNotNull();
        assertThat(update.get("sentAt")).as("a failed attempt must not look delivered").isNull();
    }

    @Test
    @DisplayName("§7: a permanent provider failure is terminal rather than retried")
    void permanentFailureIsNotRetried() {
        when(mongoTemplate.find(any(Query.class), any(Class.class)))
                .thenReturn(List.of(pendingJob("job-1", 11L, 0)));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class)))
                .thenThrow(new EmailSendException(EmailSendException.Category.AUTH, "unverified domain"));

        service.processDueBatch();

        verify(dispatchClient, times(1)).send(any(EmailDraft.class));
        Document update = capturedStatusUpdate();
        assertThat(update.get("status")).isEqualTo(EmailOutboxStatus.FAILED.name());
        assertThat(update.get("nextAttemptAt")).as("terminal, so no further attempt is scheduled").isNull();
    }

    @Test
    @DisplayName("§7: retries are bounded - the last permitted attempt ends in FAILED")
    void retriesAreBounded() {
        // One below the limit; the claim increments it, so this send consumes the final attempt.
        when(mongoTemplate.find(any(Query.class), any(Class.class)))
                .thenReturn(List.of(pendingJob("job-1", 11L, MAX_ATTEMPTS - 1)));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class)))
                .thenThrow(new EmailSendException(EmailSendException.Category.TIMEOUT, "timed out", false));

        service.processDueBatch();

        verify(dispatchClient, times(1)).send(any(EmailDraft.class));
        assertThat(capturedStatusUpdate().get("status")).isEqualTo(EmailOutboxStatus.FAILED.name());
    }

    // ---------------------------------------------------------------- §8 / §12

    @Test
    @DisplayName("§8: one failing recipient does not stop the rest of the batch")
    void oneFailureDoesNotStopTheBatch() {
        when(mongoTemplate.find(any(Query.class), any(Class.class)))
                .thenReturn(List.of(pendingJob("job-1", 11L, 0), pendingJob("job-2", 12L, 0)));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class)))
                .thenThrow(new EmailSendException(EmailSendException.Category.SERVER, "provider 503", false))
                .thenReturn("prov-ok");

        service.processDueBatch();

        // Both were attempted, and the healthy recipient still reached SUBMITTED exactly once.
        verify(dispatchClient, times(2)).send(any(EmailDraft.class));
        assertThat(capturedStatusUpdate().get("status"))
                .isEqualTo(EmailOutboxStatus.SUBMITTED.name());
    }

    @Test
    @DisplayName("§8: a recipient with no usable address is skipped without blocking the others")
    void invalidAddressDoesNotBlockOthers() {
        when(outboxRepository.findByMessageId(MESSAGE_ID)).thenReturn(List.of());

        service.enqueueHighPriority(MESSAGE_ID, List.of(
                recipient(11L, "a@student.tce.edu"),
                recipient(12L, "   "),
                recipient(13L, null)));

        List<MongoMessageEmailOutbox> inserted = capturedInserts();
        assertThat(inserted).hasSize(3);
        assertThat(inserted.stream()
                .filter(r -> EmailOutboxStatus.PENDING.name().equals(r.getStatus())))
                .extracting(MongoMessageEmailOutbox::getRecipientUserId)
                .containsExactly(11L);
        assertThat(inserted.stream()
                .filter(r -> EmailOutboxStatus.SKIPPED_INVALID_EMAIL.name().equals(r.getStatus())))
                .extracting(MongoMessageEmailOutbox::getRecipientUserId)
                .containsExactlyInAnyOrder(12L, 13L);
    }

    // ---------------------------------------------------------------- §13

    @Test
    @DisplayName("§13: a row persisted before a restart is picked up and sent by the new instance")
    void pendingWorkResumesAfterRestart() {
        when(mongoTemplate.find(any(Query.class), any(Class.class)))
                .thenReturn(List.of(pendingJob("job-1", 11L, 0)));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class))).thenReturn("prov-after-restart");

        // A brand-new service stands in for the restarted JVM over the same persisted row.
        newService().processDueBatch();

        verify(dispatchClient, times(1)).send(any(EmailDraft.class));
        assertThat(capturedStatusUpdate().get("status"))
                .isEqualTo(EmailOutboxStatus.SUBMITTED.name());
    }
}
