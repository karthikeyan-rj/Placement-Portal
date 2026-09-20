package com.college.placement.messaging.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "message_recipients")
@CompoundIndexes({
        @CompoundIndex(name = "idx_recip_msg_user_unique", def = "{'messageId': 1, 'recipientUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_recip_user_created", def = "{'recipientUserId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_recip_msg_read", def = "{'messageId': 1, 'readAt': 1}"),
        @CompoundIndex(name = "idx_recip_msg_reaction", def = "{'messageId': 1, 'reaction': 1}")
})
public class MongoMessageRecipient {

    @Id
    private String id;

    @Indexed
    private Long messageId;

    @Indexed
    private Long recipientUserId;

    private LocalDateTime deliveredAt;

    private LocalDateTime readAt;

    private String reaction;

    private LocalDateTime reactionAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}