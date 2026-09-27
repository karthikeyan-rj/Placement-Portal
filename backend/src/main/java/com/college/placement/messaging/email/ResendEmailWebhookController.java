package com.college.placement.messaging.email;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound Resend delivery events. Authenticity is established by the Svix signature
 * rather than by portal credentials, so this endpoint is unauthenticated but strictly verified.
 */
@RestController
@RequestMapping("/api/webhooks/resend")
@RequiredArgsConstructor
public class ResendEmailWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailWebhookController.class);

    private final ResendWebhookService webhookService;

    @PostMapping("/email")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature,
            @RequestBody(required = false) String body) {

        ResendWebhookService.Outcome outcome = webhookService.verify(svixId, svixTimestamp, svixSignature, body);
        return switch (outcome) {
            case APPLIED, DUPLICATE, IGNORED, UNMATCHED -> {
                // 200 acknowledges the event so the provider stops retrying it.
                yield ResponseEntity.ok().build();
            }
            case SECRET_NOT_CONFIGURED -> {
                log.warn("[EMAIL] Webhook rejected: server is not configured to verify signatures");
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            case INVALID_SIGNATURE -> {
                log.warn("[EMAIL] Webhook rejected: signature verification failed");
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            case MALFORMED -> {
                log.warn("[EMAIL] Webhook rejected: unparseable payload");
                yield ResponseEntity.badRequest().build();
            }
        };
    }
}
