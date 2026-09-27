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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "message_email_outbox")
@CompoundIndexes({
        @CompoundIndex(name = "idx_outbox_msg_user_unique", def = "{'messageId': 1, 'recipientUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_outbox_status_next", def = "{'status': 1, 'nextAttemptAt': 1}")
})
public class MongoMessageEmailOutbox {

    @Id
    private String id;

    private Long messageId;

    private Long recipientUserId;

    private String recipientEmail;

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