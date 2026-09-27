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
@Document(collection = "message_email_mock_sent")
@CompoundIndexes({
        @CompoundIndex(name = "idx_mock_sent_msg_user", def = "{'messageId': 1, 'recipientUserId': 1}")
})
public class MongoMockSentEmail {

    @Id
    private String id;

    private Long messageId;

    private Long recipientUserId;

    private String email;

    private String subject;

    private String providerMessageId;

    private LocalDateTime sentAt;
}