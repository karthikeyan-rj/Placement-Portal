package com.college.placement.messaging.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend", matchIfMissing = false)
public class ResendEmailClient implements EmailDispatchClient {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final EmailProperties props;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean isConfigured() {
        return props.getResendApiKey() != null && !props.getResendApiKey().isBlank()
                && props.getFromEmail() != null && !props.getFromEmail().isBlank();
    }

    @Override
    public String send(EmailDraft draft) throws EmailSendException {
        if (!isConfigured()) {
            throw new EmailSendException(EmailSendException.Category.OTHER, "EMAIL_NOT_CONFIGURED");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", props.getFromName() + " <" + props.getFromEmail() + ">");
        payload.put("to", List.of(draft.toEmail()));
        payload.put("subject", draft.subject());
        payload.put("html", draft.html());
        payload.put("text", draft.text());
        if (props.getReplyTo() != null && !props.getReplyTo().isBlank()) {
            payload.put("reply_to", props.getReplyTo());
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new EmailSendException(EmailSendException.Category.OTHER, "payload serialization error");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL))
                .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                .header("Authorization", "Bearer " + props.getResendApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException(EmailSendException.Category.TIMEOUT, "request interrupted");
        } catch (IOException e) {
            throw new EmailSendException(EmailSendException.Category.TIMEOUT, "network failure");
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String id = extractId(response.body());
            return id != null ? id : "resend-" + UUID.randomUUID();
        }
        throw categorize(response.statusCode(), response.body());
    }

    EmailSendException categorize(int status, String body) {
        String reason = "RESEND_ERROR status=" + status + extractErrorName(body);
        if (status == 401 || status == 403) {
            return new EmailSendException(EmailSendException.Category.AUTH, reason, true);
        }
        if (status == 429) {
            return new EmailSendException(EmailSendException.Category.RATE_LIMIT, reason, false);
        }
        if (status == 408 || status == 425) {
            return new EmailSendException(EmailSendException.Category.TIMEOUT, reason, false);
        }
        if (status >= 500) {
            return new EmailSendException(EmailSendException.Category.SERVER, reason, false);
        }
        if (status >= 400) {
            return new EmailSendException(EmailSendException.Category.INVALID_REQUEST, reason, true);
        }
        return new EmailSendException(EmailSendException.Category.OTHER, reason, true);
    }

    private String extractErrorName(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.hasNonNull("name")) {
                return " name=" + sanitizeToken(node.get("name").asText());
            }
        } catch (IOException ignored) {
            // non-JSON error body; status code alone is still reported
        }
        return "";
    }

    private String sanitizeToken(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_.-]", "");
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private String extractId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node != null && node.hasNonNull("id") ? node.get("id").asText() : null;
        } catch (IOException e) {
            return null;
        }
    }
}