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
@Document(collection = "clarification_threads")
@CompoundIndexes({
        @CompoundIndex(name = "idx_thread_msg_requester_unique", def = "{'messageId': 1, 'requesterUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_thread_sender_updated", def = "{'senderUserId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_thread_requester_updated", def = "{'requesterUserId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_thread_msg_status", def = "{'messageId': 1, 'status': 1}")
})
public class MongoClarificationThread {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long threadId;

    private Long messageId;

    private Long requesterUserId;

    private Long senderUserId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}