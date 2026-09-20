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
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "idx_msg_sender_created", def = "{'senderUserId': 1, 'createdAt': -1}")
})
public class MongoMessage {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long messageId;

    private Long senderUserId;

    private String senderRole;

    private Long senderDepartmentId;

    private String title;

    private String content;

    private String messageType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}