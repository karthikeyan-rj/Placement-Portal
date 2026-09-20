package com.college.placement.messaging.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "clarification_entries")
@CompoundIndex(name = "idx_entry_thread_created", def = "{'threadId': 1, 'createdAt': 1}")
public class MongoClarificationEntry {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long entryId;

    private Long threadId;

    private Long authorUserId;

    private String content;

    private LocalDateTime createdAt;
}