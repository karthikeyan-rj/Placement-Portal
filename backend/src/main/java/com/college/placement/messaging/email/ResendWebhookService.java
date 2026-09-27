package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoEmailWebhookEvent;
import com.college.placement.messaging.mongo.repository.EmailWebhookEventRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Verifies and applies Resend/Svix-signed email delivery webhooks.
 * Authentication is deliberately absent here: authenticity comes from the signature,
 * and every event is processed at most once via the provider event id.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResendWebhookService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_VERSION = "v1";

    private final EmailProperties props;
    private final EmailNotificationService emailNotificationService;
    private final EmailWebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public Outcome verify(String svixId, String svixTimestamp, String svixSignature, String body) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            log.warn("[EMAIL] Rejected webhook: RESEND_WEBHOOK_SECRET is not configured");
            return Outcome.SECRET_NOT_CONFIGURED;
        }
        if (isBlank(svixId) || isBlank(svixTimestamp) || isBlank(svixSignature) || body == null) {
            return Outcome.INVALID_SIGNATURE;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(svixTimestamp.trim());
        } catch (NumberFormatException e) {
            return Outcome.INVALID_SIGNATURE;
        }
        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (ageSeconds > props.getWebhookToleranceSeconds()) {
            log.warn("[EMAIL] Rejected webhook with stale timestamp (age={}s)", ageSeconds);
            return Outcome.INVALID_SIGNATURE;
        }
        if (!matchesAnySignature(svixId.trim(), svixTimestamp.trim(), body, svixSignature)) {
            return Outcome.INVALID_SIGNATURE;
        }
        return handleEvent(svixId.trim(), body);
    }

    private Outcome handleEvent(String eventId, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return Outcome.MALFORMED;
        }
        String eventType = text(root, "type");
        String providerMessageId = text(root.path("data"), "email_id");
        if (eventType == null || providerMessageId == null) {
            return Outcome.MALFORMED;
        }
        try {
            eventRepository.insert(MongoEmailWebhookEvent.builder()
                    .id(eventId)
                    .eventType(eventType)
                    .providerMessageId(providerMessageId)
                    .processedAt(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException alreadyProcessed) {
            return Outcome.DUPLICATE;
        }
        EmailOutboxStatus target = mapEvent(eventType);
        if (target == null) {
            log.info("[EMAIL] Ignored unmapped webhook event type {}", sanitizeToken(eventType));
            return Outcome.IGNORED;
        }
        boolean updated = emailNotificationService.applyDeliveryEvent(providerMessageId, target, eventType);
        if (!updated) {
            log.info("[EMAIL] No outbox row for provider message id {} on event {}", providerMessageId, eventType);
        }
        return updated ? Outcome.APPLIED : Outcome.UNMATCHED;
    }

    private EmailOutboxStatus mapEvent(String eventType) {
        return switch (eventType) {
            case "email.sent" -> EmailOutboxStatus.SUBMITTED;
            case "email.delivered" -> EmailOutboxStatus.DELIVERED;
            case "email.delivery_delayed" -> EmailOutboxStatus.DELAYED;
            case "email.bounced" -> EmailOutboxStatus.BOUNCED;
            case "email.complained" -> EmailOutboxStatus.COMPLAINED;
            case "email.suppressed" -> EmailOutboxStatus.SUPPRESSED;
            default -> null;
        };
    }

    private boolean matchesAnySignature(String id, String timestamp, String body, String header) {
        byte[] key = signingKey(props.getWebhookSecret());
        if (key == null) {
            return false;
        }
        Mac mac;
        try {
            mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        } catch (Exception e) {
            log.warn("[EMAIL] Webhook signature verification unavailable");
            return false;
        }
        byte[] digest = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        byte[] expected = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(digest));
        for (String part : header.trim().split("\\s+")) {
            if (!part.startsWith(SIGNATURE_VERSION + ",")) {
                continue;
            }
            byte[] candidate;
            try {
                candidate = Base64.getDecoder().decode(part.substring(SIGNATURE_VERSION.length() + 1));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, candidate)) {
                return true;
            }
        }
        return false;
    }

    private byte[] signingKey(String secret) {
        String trimmed = secret.trim();
        try {
            if (trimmed.startsWith("whsec_")) {
                return Base64.getDecoder().decode(trimmed.substring("whsec_".length()));
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to url-safe decoding
        }
        try {
            return Base64.getUrlDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : sanitizeToken(value.asText());
    }

    private String sanitizeToken(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_.:@-]", "");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Outcome {
        APPLIED,
        DUPLICATE,
        IGNORED,
        UNMATCHED,
        MALFORMED,
        INVALID_SIGNATURE,
        SECRET_NOT_CONFIGURED
    }
}
