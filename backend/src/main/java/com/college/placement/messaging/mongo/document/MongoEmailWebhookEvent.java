package com.college.placement.messaging.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One row per provider webhook event id. The _id is the provider event id, so the unique
 * primary key gives exactly-once processing even when the provider retries a delivery.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "email_webhook_events")
public class MongoEmailWebhookEvent {

    @Id
    private String id;

    private String eventType;

    private String providerMessageId;

    @Indexed(expireAfter = "P30D")
    private LocalDateTime processedAt;
}
