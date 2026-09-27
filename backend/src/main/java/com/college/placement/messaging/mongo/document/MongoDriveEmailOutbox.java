package com.college.placement.messaging.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Outbox for Placement Drive notification emails. One row per (drive, recipient, event)
 * so a repeated status transition can never email the same student twice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "drive_email_outbox")
@CompoundIndexes({
        @CompoundIndex(name = "idx_drive_outbox_dedupe_unique",
                def = "{'driveId': 1, 'recipientUserId': 1, 'eventKey': 1}", unique = true),
        @CompoundIndex(name = "idx_drive_outbox_status_next",
                def = "{'status': 1, 'nextAttemptAt': 1}"),
        @CompoundIndex(name = "idx_drive_outbox_provider", def = "{'providerMessageId': 1}")
})
public class MongoDriveEmailOutbox {

    @Id
    private String id;

    private Long driveId;

    /** Stable event identity, e.g. REGISTRATION_OPEN. Part of the dedupe key. */
    private String eventKey;

    private String batchId;

    private Long recipientUserId;

    private String recipientEmail;

    /**
     * Rendered content captured at enqueue time so the worker never needs to read drive
     * tables and pending jobs survive an application restart unchanged.
     */
    private String subject;

    private String html;

    private String text;

    private String status;

    private Integer attempts;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime startedAt;

    private String providerMessageId;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime bouncedAt;

    private String lastEventName;

    private LocalDateTime lastEventAt;
}
