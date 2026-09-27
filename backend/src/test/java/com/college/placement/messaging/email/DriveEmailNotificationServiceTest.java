package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoDriveEmailOutbox;
import com.college.placement.messaging.mongo.repository.DriveEmailOutboxRepository;
import com.college.placement.placement.EligibilityCriteria;
import com.college.placement.placement.EligibilityCriteriaRepository;
import com.college.placement.placement.dto.DriveRecipientProjection;
import com.college.placement.user.UserRepository;

import com.mongodb.client.result.UpdateResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fanout contract for Placement Drive REGISTRATION_OPEN notifications.
 *
 * <p>The properties under test are the ones that decide whether a real student gets exactly
 * one email: recipients come from the eligibility rules, the provider is never called during
 * enqueue, dedupe collapses repeated (drive, recipient, event) rows, and a missing address
 * is skipped rather than retried forever.
 */
class DriveEmailNotificationServiceTest {

    private MongoTemplate mongoTemplate;
    private DriveEmailOutboxRepository outboxRepository;
    private UserRepository userRepository;
    private EligibilityCriteriaRepository eligibilityRepository;
    private EmailDispatchClient dispatchClient;
    private EmailProperties props;
    private DriveEmailNotificationService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        outboxRepository = mock(DriveEmailOutboxRepository.class);
        userRepository = mock(UserRepository.class);
        eligibilityRepository = mock(EligibilityCriteriaRepository.class);
        dispatchClient = mock(EmailDispatchClient.class);
        props = new EmailProperties();
        props.setEnabled(true);
        props.setProvider("resend");
        props.setResendApiKey("configured");
        props.setFromEmail("no-reply@verified.example");
        when(mongoTemplate.insert(anyList(), eq(MongoDriveEmailOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mongoTemplate.updateMulti(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        service = new DriveEmailNotificationService(props, mongoTemplate, outboxRepository,
                userRepository, eligibilityRepository, dispatchClient);
    }

    private DriveRecipientProjection recipient(long id, String email) {
        return new DriveRecipientProjection() {
            @Override
            public Long getUserId() {
                return id;
            }

            @Override
            public String getEmail() {
                return email;
            }
        };
    }

    private List<DriveRecipientProjection> recipients(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> recipient(i, "student" + i + "@college.edu"))
                .toList();
    }

    private List<MongoDriveEmailOutbox> captureInserted() {
        ArgumentCaptor<List<MongoDriveEmailOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .insert(captor.capture(), eq(MongoDriveEmailOutbox.class));
        List<MongoDriveEmailOutbox> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    private List<Object> fieldsOf(List<Update> updates, String field) {
        List<Object> values = new ArrayList<>();
        for (Update u : updates) {
            Object set = u.getUpdateObject().get("$set");
            if (set instanceof org.bson.Document doc && doc.get(field) != null) {
                values.add(doc.get(field));
            }
        }
        return values;
    }

    private List<Object> statusesOf(List<Update> updates) {
        return fieldsOf(updates, "status");
    }

    @Test
    @DisplayName("1500 eligible recipients produce 1500 queued rows and no provider call")
    void largeFanoutIsChunkedAndNeverCallsProvider() {
        when(eligibilityRepository.findByPlacementDriveId(1L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(1500));

        int queued = service.enqueueRegistrationOpen(1L, "SDE", "Acme", null, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 20), "Campus");

        assertThat(queued).isEqualTo(1500);
        List<MongoDriveEmailOutbox> rows = captureInserted();
        assertThat(rows).hasSize(1500);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.PENDING.name());
            assertThat(row.getEventKey()).isEqualTo(DriveEmailNotificationService.EVENT_REGISTRATION_OPEN);
            assertThat(row.getDriveId()).isEqualTo(1L);
            assertThat(row.getAttempts()).isZero();
            assertThat(row.getRecipientEmail()).endsWith("@college.edu");
            assertThat(row.getSubject()).isEqualTo("Placement Opportunity: Acme — SDE");
            assertThat(row.getHtml()).contains("Acme");
            assertThat(row.getText()).isNotBlank();
        });
        assertThat(rows.stream().map(MongoDriveEmailOutbox::getRecipientUserId).distinct().count())
                .isEqualTo(1500);
        assertThat(rows.stream().map(MongoDriveEmailOutbox::getBatchId).distinct().count()).isEqualTo(1);
        verifyNoInteractions(dispatchClient);
    }

    @Test
    @DisplayName("the drive notification uses the required subject and body template")
    void driveNotificationMatchesRequiredTemplate() {
        when(eligibilityRepository.findByPlacementDriveId(20L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(1));

        service.enqueueRegistrationOpen(20L, "Backend Developer", "Cobalt Industries",
                new BigDecimal("8.50"), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 10, 30), "Chennai");

        MongoDriveEmailOutbox row = captureInserted().get(0);
        assertThat(row.getSubject()).isEqualTo("Placement Opportunity: Cobalt Industries — Backend Developer");
        assertThat(row.getHtml())
                .contains("Cobalt Industries")
                .contains("Backend Developer")
                .contains("8.50 LPA")
                .contains("Registration deadline")
                .contains("2026-10-30")
                .contains("Drive date")
                .contains("2026-11-20")
                .contains("eligible")
                .contains("View Drive");
        assertThat(row.getText())
                .contains("Company: Cobalt Industries")
                .contains("Role: Backend Developer")
                .contains("CTC / Package: 8.50 LPA")
                .contains("View Drive:");
    }

    @Test
    @DisplayName("a drive with no package omits the CTC line instead of printing null")
    void missingPackageIsOmitted() {
        when(eligibilityRepository.findByPlacementDriveId(21L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(1));

        service.enqueueRegistrationOpen(21L, "SDE", "Acme", null, null, null, null);

        MongoDriveEmailOutbox row = captureInserted().get(0);
        assertThat(row.getHtml()).doesNotContain("null").doesNotContain("CTC");
        assertThat(row.getText()).doesNotContain("null").doesNotContain("CTC");
    }

    @Test
    @DisplayName("a newline in a company name cannot inject extra email headers")
    void subjectIsStrippedOfControlCharacters() {
        when(eligibilityRepository.findByPlacementDriveId(22L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(1));

        service.enqueueRegistrationOpen(22L, "SDE", "Acme\r\nBcc: victim@college.edu",
                null, null, null, null);

        assertThat(captureInserted().get(0).getSubject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("500 recipients fit a single insert chunk")
    void fiveHundredRecipientsAreOneChunk() {
        when(eligibilityRepository.findByPlacementDriveId(2L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(500));

        assertThat(service.enqueueRegistrationOpen(2L, "SDE", "Acme", null, null, null, null)).isEqualTo(500);
        verify(mongoTemplate, org.mockito.Mockito.times(1))
                .insert(anyList(), eq(MongoDriveEmailOutbox.class));
    }

    @Test
    @DisplayName("duplicate resolver rows collapse to one outbox row per user")
    void duplicateRecipientsAreDeduplicated() {
        List<DriveRecipientProjection> withDupes = new ArrayList<>();
        withDupes.add(recipient(7L, "a@college.edu"));
        withDupes.add(recipient(7L, "a@college.edu"));
        withDupes.add(recipient(8L, "b@college.edu"));
        when(eligibilityRepository.findByPlacementDriveId(3L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(withDupes);

        assertThat(service.enqueueRegistrationOpen(3L, "SDE", "Acme", null, null, null, null)).isEqualTo(2);
        assertThat(captureInserted()).hasSize(2);
    }

    @Test
    @DisplayName("a missing or malformed address is skipped, not queued for retry")
    void invalidAddressesAreSkipped() {
        List<DriveRecipientProjection> mixed = new ArrayList<>();
        mixed.add(recipient(1L, "good@college.edu"));
        mixed.add(recipient(2L, null));
        mixed.add(recipient(3L, "   "));
        mixed.add(recipient(4L, "not-an-email"));
        mixed.add(recipient(5L, "bad\r\nBcc:victim@x.com@college.edu"));
        when(eligibilityRepository.findByPlacementDriveId(4L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(mixed);

        // All five rows are recorded so the PO can see the skipped ones, but only one is sendable.
        assertThat(service.enqueueRegistrationOpen(4L, "SDE", "Acme", null, null, null, null)).isEqualTo(5);
        List<MongoDriveEmailOutbox> rows = captureInserted();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).getStatus()).isEqualTo(EmailOutboxStatus.PENDING.name());
        assertThat(rows.subList(1, 5)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SKIPPED_INVALID_EMAIL.name());
            assertThat(row.getRecipientEmail()).isNull();
            assertThat(row.getNextAttemptAt()).isNull();
            assertThat(row.getLastError()).isNotBlank();
        });
    }

    @Test
    @DisplayName("nothing is queued while email notifications are disabled")
    void disabledEmailQueuesNothing() {
        props.setEnabled(false);
        when(eligibilityRepository.findByPlacementDriveId(5L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(recipients(50));

        assertThat(service.enqueueRegistrationOpen(5L, "SDE", "Acme", null, null, null, null)).isZero();
        verify(mongoTemplate, never()).insert(anyList(), eq(MongoDriveEmailOutbox.class));
    }

    @Test
    @DisplayName("a drive with no eligible recipients queues nothing")
    void emptyRecipientSetQueuesNothing() {
        when(eligibilityRepository.findByPlacementDriveId(6L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria()).thenReturn(List.of());

        assertThat(service.enqueueRegistrationOpen(6L, "SDE", "Acme", null, null, null, null)).isZero();
        verify(mongoTemplate, never()).insert(anyList(), eq(MongoDriveEmailOutbox.class));
    }

    @Test
    @DisplayName("criteria with no department allow-list resolves every eligible student")
    void criteriaWithoutDepartmentsUsesUnrestrictedQuery() {
        EligibilityCriteria criteria = EligibilityCriteria.builder().build();
        when(eligibilityRepository.findByPlacementDriveId(7L)).thenReturn(Optional.of(criteria));
        when(userRepository.findDriveRecipientsWithCriteria(anyBoolean(), anyList(), any(), any()))
                .thenReturn(recipients(3));

        assertThat(service.enqueueRegistrationOpen(7L, "SDE", "Acme", null, null, null, null)).isEqualTo(3);

        ArgumentCaptor<Boolean> allDepts = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<Long>> deptIds = ArgumentCaptor.forClass(List.class);
        verify(userRepository).findDriveRecipientsWithCriteria(allDepts.capture(), deptIds.capture(),
                any(), any());
        assertThat(allDepts.getValue()).isTrue();
        assertThat(deptIds.getValue()).containsExactly(-1L);
    }

    @Test
    @DisplayName("allowed departments are read by id so a detached criteria cannot break the fanout")
    void detachedCriteriaDoesNotTriggerLazyInitialization() {
        // A real detached EligibilityCriteria throws on allowedDepartments access outside a
        // session. The outbox worker and the after-commit listener both run without one, so the
        // resolver must never touch the lazy collection.
        EligibilityCriteria detached = mock(EligibilityCriteria.class);
        when(detached.getAllowedDepartments()).thenThrow(
                new org.hibernate.LazyInitializationException("no Session"));
        when(detached.getMinCgpa()).thenReturn(new BigDecimal("6.00"));
        when(detached.getMaxActiveBacklogs()).thenReturn(2);
        when(eligibilityRepository.findByPlacementDriveId(30L)).thenReturn(Optional.of(detached));
        when(eligibilityRepository.findAllowedDepartmentIdsByDriveId(30L)).thenReturn(List.of(1L, 3L));
        when(userRepository.findDriveRecipientsWithCriteria(anyBoolean(), anyList(), any(), any()))
                .thenReturn(recipients(4));

        int queued = service.enqueueRegistrationOpen(30L, "SDE", "Acme", null, null, null, null);

        assertThat(queued).isEqualTo(4);
        ArgumentCaptor<Boolean> allDepts = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<Long>> deptIds = ArgumentCaptor.forClass(List.class);
        verify(userRepository).findDriveRecipientsWithCriteria(allDepts.capture(), deptIds.capture(),
                eq(new BigDecimal("6.00")), eq(2));
        assertThat(allDepts.getValue()).isFalse();
        assertThat(deptIds.getValue()).containsExactly(1L, 3L);
        verify(eligibilityRepository).findAllowedDepartmentIdsByDriveId(30L);
    }

    @Test
    @DisplayName("an empty department id list means the drive is open to every department")
    void emptyDepartmentIdListIsTreatedAsUnrestricted() {
        EligibilityCriteria criteria = EligibilityCriteria.builder()
                .minCgpa(new BigDecimal("7.00")).build();
        when(eligibilityRepository.findByPlacementDriveId(31L)).thenReturn(Optional.of(criteria));
        when(eligibilityRepository.findAllowedDepartmentIdsByDriveId(31L)).thenReturn(List.of());
        when(userRepository.findDriveRecipientsWithCriteria(anyBoolean(), anyList(), any(), any()))
                .thenReturn(recipients(2));

        assertThat(service.enqueueRegistrationOpen(31L, "SDE", "Acme", null, null, null, null)).isEqualTo(2);

        ArgumentCaptor<Boolean> allDepts = ArgumentCaptor.forClass(Boolean.class);
        verify(userRepository).findDriveRecipientsWithCriteria(allDepts.capture(), anyList(), any(), any());
        assertThat(allDepts.getValue()).isTrue();
    }

    @Test
    @DisplayName("a transient provider failure is rescheduled instead of failed")
    void transientFailureIsRetried() {
        MongoDriveEmailOutbox job = MongoDriveEmailOutbox.builder()
                .id("job-1").driveId(8L).eventKey(DriveEmailNotificationService.EVENT_REGISTRATION_OPEN)
                .recipientUserId(1L).recipientEmail("s@college.edu")
                .subject("subject").html("<p>hi</p>").text("hi")
                .status(EmailOutboxStatus.PENDING.name()).attempts(0).build();
        when(mongoTemplate.find(any(), eq(MongoDriveEmailOutbox.class))).thenReturn(List.of(job));
        when(mongoTemplate.updateFirst(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any())).thenThrow(
                new EmailSendException(EmailSendException.Category.SERVER, "RESEND_ERROR status=503", false));

        service.processDueBatch();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(any(), update.capture(), eq(MongoDriveEmailOutbox.class));
        assertThat(statusesOf(update.getAllValues()))
                .contains(EmailOutboxStatus.PENDING.name());
    }

    @Test
    @DisplayName("a permanent provider failure is failed immediately, not retried")
    void permanentFailureIsNotRetried() {
        MongoDriveEmailOutbox job = MongoDriveEmailOutbox.builder()
                .id("job-2").driveId(9L).eventKey(DriveEmailNotificationService.EVENT_REGISTRATION_OPEN)
                .recipientUserId(1L).recipientEmail("s@college.edu")
                .subject("subject").html("<p>hi</p>").text("hi")
                .status(EmailOutboxStatus.PENDING.name()).attempts(0).build();
        when(mongoTemplate.find(any(), eq(MongoDriveEmailOutbox.class))).thenReturn(List.of(job));
        when(mongoTemplate.updateFirst(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any())).thenThrow(
                new EmailSendException(EmailSendException.Category.INVALID_REQUEST,
                        "RESEND_ERROR status=422 name=validation_error", true));

        service.processDueBatch();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(any(), update.capture(), eq(MongoDriveEmailOutbox.class));
        assertThat(statusesOf(update.getAllValues()))
                .contains(EmailOutboxStatus.FAILED.name());
    }

    @Test
    @DisplayName("an unconfigured provider marks rows CONFIG_ERROR instead of pretending to send")
    void unconfiguredProviderMarksConfigError() {
        MongoDriveEmailOutbox job = MongoDriveEmailOutbox.builder()
                .id("job-3").driveId(10L).eventKey(DriveEmailNotificationService.EVENT_REGISTRATION_OPEN)
                .recipientUserId(1L).recipientEmail("s@college.edu")
                .subject("s").html("h").text("t")
                .status(EmailOutboxStatus.PENDING.name()).attempts(0).build();
        when(mongoTemplate.find(any(), eq(MongoDriveEmailOutbox.class))).thenReturn(List.of(job));
        when(mongoTemplate.updateFirst(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(dispatchClient.isConfigured()).thenReturn(false);

        service.processDueBatch();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(any(), update.capture(), eq(MongoDriveEmailOutbox.class));
        assertThat(statusesOf(update.getAllValues()))
                .contains(EmailOutboxStatus.CONFIG_ERROR.name());
        verify(dispatchClient, never()).send(any());
    }

    @Test
    @DisplayName("a successful send records the provider id as SUBMITTED, not DELIVERED")
    void successfulSendIsRecordedAsSubmitted() {
        MongoDriveEmailOutbox job = MongoDriveEmailOutbox.builder()
                .id("job-4").driveId(11L).eventKey(DriveEmailNotificationService.EVENT_REGISTRATION_OPEN)
                .recipientUserId(1L).recipientEmail("s@college.edu")
                .subject("s").html("h").text("t")
                .status(EmailOutboxStatus.PENDING.name()).attempts(0).build();
        when(mongoTemplate.find(any(), eq(MongoDriveEmailOutbox.class))).thenReturn(List.of(job));
        when(mongoTemplate.updateFirst(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any())).thenReturn("resend-provider-id");

        service.processDueBatch();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(any(), update.capture(), eq(MongoDriveEmailOutbox.class));
        assertThat(statusesOf(update.getAllValues()))
                .contains(EmailOutboxStatus.SUBMITTED.name());
        assertThat(fieldsOf(update.getAllValues(), "providerMessageId"))
                .contains("resend-provider-id");
    }

    @Test
    @DisplayName("a late non-terminal event cannot downgrade a delivered row")
    void deliveryStateIsMonotonic() {
        when(mongoTemplate.updateFirst(any(), any(Update.class), eq(MongoDriveEmailOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        assertThat(service.applyDeliveryEvent("resend-1", EmailOutboxStatus.DELIVERED, "email.delivered"))
                .isTrue();

        ArgumentCaptor<org.springframework.data.mongodb.core.query.Query> query =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        verify(mongoTemplate).updateFirst(query.capture(), any(Update.class), eq(MongoDriveEmailOutbox.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains(EmailOutboxStatus.DELIVERED.name())
                .contains(EmailOutboxStatus.BOUNCED.name());
    }

    @Test
    @DisplayName("a drive outbox row queued before a restart still carries its content")
    void queuedRowsCarryContentForRestartSafety() {
        when(eligibilityRepository.findByPlacementDriveId(12L)).thenReturn(Optional.empty());
        when(userRepository.findDriveRecipientsWithoutCriteria())
                .thenReturn(List.of(recipient(1L, "s@college.edu")));

        service.enqueueRegistrationOpen(12L, "SDE", "Acme", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 20), "Campus");

        MongoDriveEmailOutbox row = captureInserted().get(0);
        assertThat(row.getSubject()).isNotBlank();
        assertThat(row.getHtml()).contains("2026-10-20");
        assertThat(row.getText()).contains("Campus");
        assertThat(row.getBatchId()).isNotBlank();
    }
}
