package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoEmailWebhookEvent;
import com.college.placement.messaging.mongo.repository.EmailWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security and idempotency contract for inbound delivery webhooks.
 *
 * <p>The endpoint is unauthenticated by design, so the signature is the only thing standing
 * between a stranger and forged delivery statuses. These tests cover authenticity, replay
 * rejection and exactly-once application.
 */
class ResendWebhookServiceTest {

    private static final String SECRET_PLAIN = "topsecret-signing-key";
    private static final String SECRET_WHSEC = "whsec_"
            + Base64.getEncoder().encodeToString(SECRET_PLAIN.getBytes(StandardCharsets.UTF_8));

    private EmailProperties props;
    private EmailNotificationService emailNotificationService;
    private EmailWebhookEventRepository eventRepository;
    private ResendWebhookService service;

    @BeforeEach
    void setUp() {
        props = new EmailProperties();
        props.setWebhookSecret(SECRET_WHSEC);
        emailNotificationService = mock(EmailNotificationService.class);
        eventRepository = mock(EmailWebhookEventRepository.class);
        service = new ResendWebhookService(props, emailNotificationService, eventRepository, new ObjectMapper());
    }

    private String payload(String type, String emailId) {
        return "{\"type\":\"" + type + "\",\"created_at\":\"2026-01-01T00:00:00.000Z\","
                + "\"data\":{\"email_id\":\"" + emailId + "\"}}";
    }

    private String sign(String id, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET_PLAIN.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String now() {
        return String.valueOf(System.currentTimeMillis() / 1000L);
    }

    @Test
    @DisplayName("correctly signed delivery event marks the outbox row DELIVERED")
    void signedDeliveryEventIsApplied() {
        String body = payload("email.delivered", "msg-1");
        String ts = now();
        when(emailNotificationService.applyDeliveryEvent("msg-1", EmailOutboxStatus.DELIVERED, "email.delivered"))
                .thenReturn(true);

        ResendWebhookService.Outcome outcome = service.verify("evt_1", ts, sign("evt_1", ts, body), body);

        assertThat(outcome).isEqualTo(ResendWebhookService.Outcome.APPLIED);
        verify(emailNotificationService).applyDeliveryEvent("msg-1", EmailOutboxStatus.DELIVERED, "email.delivered");
    }

    @Test
    @DisplayName("provider acceptance is recorded as SUBMITTED and never as DELIVERED")
    void sentEventDoesNotClaimDelivery() {
        String body = payload("email.sent", "msg-2");
        String ts = now();
        service.verify("evt_2", ts, sign("evt_2", ts, body), body);
        verify(emailNotificationService).applyDeliveryEvent("msg-2", EmailOutboxStatus.SUBMITTED, "email.sent");
    }

    @Test
    @DisplayName("bounce and complaint are terminal states")
    void bounceAndComplaintMapToTerminalStates() {
        String bounce = payload("email.bounced", "msg-3");
        String complaint = payload("email.complained", "msg-4");
        String ts = now();
        service.verify("evt_3", ts, sign("evt_3", ts, bounce), bounce);
        service.verify("evt_4", ts, sign("evt_4", ts, complaint), complaint);
        verify(emailNotificationService).applyDeliveryEvent("msg-3", EmailOutboxStatus.BOUNCED, "email.bounced");
        verify(emailNotificationService).applyDeliveryEvent("msg-4", EmailOutboxStatus.COMPLAINED, "email.complained");
    }

    @Test
    @DisplayName("a tampered body is rejected and no status is applied")
    void tamperedBodyIsRejected() {
        String original = payload("email.delivered", "msg-5");
        String ts = now();
        String signature = sign("evt_5", ts, original);

        ResendWebhookService.Outcome outcome =
                service.verify("evt_5", ts, signature, payload("email.delivered", "attacker-controlled"));

        assertThat(outcome).isEqualTo(ResendWebhookService.Outcome.INVALID_SIGNATURE);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
        verify(eventRepository, never()).insert(any(MongoEmailWebhookEvent.class));
    }

    @Test
    @DisplayName("a signature computed with the wrong secret is rejected")
    void wrongSecretIsRejected() {
        String body = payload("email.delivered", "msg-6");
        String ts = now();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("attacker-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String forged = "v1," + Base64.getEncoder().encodeToString(
                    mac.doFinal(("evt_6." + ts + "." + body).getBytes(StandardCharsets.UTF_8)));
            assertThat(service.verify("evt_6", ts, forged, body))
                    .isEqualTo(ResendWebhookService.Outcome.INVALID_SIGNATURE);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("a replayed old timestamp is rejected")
    void staleTimestampIsRejected() {
        String body = payload("email.delivered", "msg-7");
        String oldTs = String.valueOf(System.currentTimeMillis() / 1000L - 4000);
        assertThat(service.verify("evt_7", oldTs, sign("evt_7", oldTs, body), body))
                .isEqualTo(ResendWebhookService.Outcome.INVALID_SIGNATURE);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("when no webhook secret is configured every event is refused")
    void unconfiguredSecretRefusesEverything() {
        props.setWebhookSecret("");
        String body = payload("email.delivered", "msg-8");
        String ts = now();
        assertThat(service.verify("evt_8", ts, sign("evt_8", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.SECRET_NOT_CONFIGURED);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("a provider retry of the same event id is applied only once")
    void duplicateEventIsAppliedOnce() {
        String body = payload("email.delivered", "msg-9");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        assertThat(service.verify("evt_9", ts, sign("evt_9", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.DUPLICATE);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("the event id is what gets recorded, so retries collapse onto it")
    void eventIdIsPersistedForDedupe() {
        String body = payload("email.delivered", "msg-10");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        service.verify("evt_10", ts, sign("evt_10", ts, body), body);

        ArgumentCaptor<MongoEmailWebhookEvent> captor = ArgumentCaptor.forClass(MongoEmailWebhookEvent.class);
        verify(eventRepository, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("evt_10");
        assertThat(captor.getValue().getEventType()).isEqualTo("email.delivered");
    }

    @Test
    @DisplayName("an unmapped event type is acknowledged without touching outbox state")
    void unmappedEventIsIgnored() {
        String body = payload("email.opened", "msg-11");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.verify("evt_11", ts, sign("evt_11", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.IGNORED);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("multiple space separated v1 signatures are all accepted as candidates")
    void multipleSignaturesAreSupported() {
        String body = payload("email.delivered", "msg-12");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        String header = "v1,b3RoZXJzaWduYXR1cmU= " + sign("evt_12", ts, body);
        assertThat(service.verify("evt_12", ts, header, body)).isEqualTo(ResendWebhookService.Outcome.APPLIED);
    }

    @Test
    @DisplayName("missing signature headers are rejected")
    void missingHeadersAreRejected() {
        String body = payload("email.delivered", "msg-13");
        assertThat(service.verify(null, now(), null, body))
                .isEqualTo(ResendWebhookService.Outcome.INVALID_SIGNATURE);
        assertThat(service.verify("evt_13", "not-a-number", "v1,zzz", body))
                .isEqualTo(ResendWebhookService.Outcome.INVALID_SIGNATURE);
        verify(emailNotificationService, never()).applyDeliveryEvent(any(), any(), any());
    }

    @Test
    @DisplayName("an unparseable but correctly signed payload is reported as malformed")
    void malformedPayloadIsReported() {
        String body = "not json at all";
        String ts = now();
        assertThat(service.verify("evt_14", ts, sign("evt_14", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.MALFORMED);
    }

    @Test
    @DisplayName("a signed payload missing the provider id is malformed, not applied")
    void missingProviderIdIsMalformed() {
        String body = "{\"type\":\"email.delivered\",\"data\":{}}";
        String ts = now();
        assertThat(service.verify("evt_15", ts, sign("evt_15", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.MALFORMED);
        verify(eventRepository, never()).insert(any(MongoEmailWebhookEvent.class));
    }

    @Test
    @DisplayName("an event for an unknown provider id is acknowledged but applies nothing")
    void unmatchedProviderIdIsAcknowledged() {
        String body = payload("email.delivered", "msg-unknown");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(false);

        assertThat(service.verify("evt_16", ts, sign("evt_16", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.UNMATCHED);
    }

    @Test
    @DisplayName("delay events are distinguished from delivery")
    void delayEventMapsToDelayed() {
        String body = payload("email.delivery_delayed", "msg-17");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        service.verify("evt_17", ts, sign("evt_17", ts, body), body);
        verify(emailNotificationService).applyDeliveryEvent("msg-17", EmailOutboxStatus.DELAYED, "email.delivery_delayed");
    }

    @Test
    @DisplayName("a suppression event maps to the terminal SUPPRESSED state")
    void suppressedEventMapsToSuppressed() {
        String body = payload("email.suppressed", "msg-19");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        assertThat(service.verify("evt_19", ts, sign("evt_19", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.APPLIED);
        verify(emailNotificationService)
                .applyDeliveryEvent("msg-19", EmailOutboxStatus.SUPPRESSED, "email.suppressed");
    }

    @Test
    @DisplayName("every provider terminal event has a distinct outbox state")
    void allTerminalEventsAreMapped() {
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        String[][] cases = {
                {"email.sent", "SUBMITTED"},
                {"email.delivered", "DELIVERED"},
                {"email.delivery_delayed", "DELAYED"},
                {"email.bounced", "BOUNCED"},
                {"email.complained", "COMPLAINED"},
                {"email.suppressed", "SUPPRESSED"},
        };
        for (int i = 0; i < cases.length; i++) {
            String body = payload(cases[i][0], "msg-t" + i);
            String ts = now();
            assertThat(service.verify("evt_t" + i, ts, sign("evt_t" + i, ts, body), body))
                    .as("event %s", cases[i][0])
                    .isEqualTo(ResendWebhookService.Outcome.APPLIED);
        }
        for (int i = 0; i < cases.length; i++) {
            verify(emailNotificationService).applyDeliveryEvent(
                    eq("msg-t" + i), eq(EmailOutboxStatus.valueOf(cases[i][1])), eq(cases[i][0]));
        }
    }

    @Test
    @DisplayName("repository lookups are not required to accept an event")
    void dedupeReliesOnInsertNotLookup() {
        when(eventRepository.findById(any())).thenReturn(Optional.empty());
        String body = payload("email.delivered", "msg-18");
        String ts = now();
        when(eventRepository.insert(any(MongoEmailWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(emailNotificationService.applyDeliveryEvent(any(), any(), any())).thenReturn(true);

        assertThat(service.verify("evt_18", ts, sign("evt_18", ts, body), body))
                .isEqualTo(ResendWebhookService.Outcome.APPLIED);
        verify(eventRepository, never()).findById(any());
    }
}
