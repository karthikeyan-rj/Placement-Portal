package com.college.placement.messaging.mongo.service;

import com.college.placement.messaging.mongo.document.MongoClarificationEntry;
import com.college.placement.messaging.mongo.document.MongoClarificationThread;
import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageRecipient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.mongodb.enabled", "app.mongodb.migrate-enabled"},
        havingValue = "true",
        matchIfMissing = false)
public class MongoBackfillRunner implements CommandLineRunner {

    private static final String MESSAGE_SQL =
            "SELECT m.id, m.sender_id, u.role AS sender_role, u.department_id AS sender_department_id, "
                    + "m.title, m.content, m.message_type, m.created_at "
                    + "FROM messages m JOIN users u ON u.id = m.sender_id "
                    + "WHERE m.id > ? ORDER BY m.id LIMIT 200";

    private static final String RECIPIENT_SQL =
            "SELECT r.id, r.message_id, r.recipient_id, r.delivered_at, r.read_at, r.created_at, r.updated_at "
                    + "FROM message_recipients r WHERE r.id > ? ORDER BY r.id LIMIT 500";

    private static final String THREAD_SQL =
            "SELECT t.id, t.message_id, t.requester_id, t.sender_id, t.status, t.created_at, t.updated_at "
                    + "FROM clarification_threads t WHERE t.id > ? ORDER BY t.id LIMIT 200";

    private static final String ENTRY_SQL =
            "SELECT e.id, e.thread_id, e.author_id, e.content, e.created_at "
                    + "FROM clarification_entries e WHERE e.id > ? ORDER BY e.id LIMIT 500";

    private static final int MESSAGE_BATCH = 200;
    private static final int RECIPIENT_BATCH = 500;
    private static final int THREAD_BATCH = 200;
    private static final int ENTRY_BATCH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final MongoSequenceService sequenceService;

    private record ReactionRow(String reaction, LocalDateTime createdAt) {
    }

    @Override
    public void run(String... args) {
        long started = System.nanoTime();
        log.info("[MIGRATE] START");
        seedSequences();
        Set<Long> existingMessages = existingIds("messages", "messageId");
        Set<Long> existingThreads = existingIds("clarification_threads", "threadId");
        Set<Long> existingEntries = existingIds("clarification_entries", "entryId");
        Set<String> existingRecipients = existingRecipientKeys();
        log.info("[MIGRATE] existing mongo docs messages={} recipients={} threads={} entries={}",
                existingMessages.size(), existingRecipients.size(), existingThreads.size(), existingEntries.size());
        long messages = backfillMessages(existingMessages);
        long recipients = backfillRecipients(existingRecipients);
        long threads = backfillThreads(existingThreads);
        long entries = backfillEntries(existingEntries);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        log.info("[MIGRATE] DONE messages={} recipients={} threads={} entries={} elapsedMs={}",
                messages, recipients, threads, entries, String.format("%.1f", elapsedMs));
    }

    private void seedSequences() {
        sequenceService.seedToMax("message", maxId("messages"));
        sequenceService.seedToMax("clarificationThread", maxId("clarification_threads"));
        sequenceService.seedToMax("clarificationEntry", maxId("clarification_entries"));
        log.info("[MIGRATE] sequences seeded to postgres max ids");
    }

    private long maxId(String table) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private Set<Long> existingIds(String collection, String field) {
        Set<Long> ids = new HashSet<>();
        mongoTemplate.getCollection(collection)
                .find().projection(new org.bson.Document(field, 1)).batchSize(10000)
                .forEach(doc -> ids.add(doc.getLong(field)));
        return ids;
    }

    private Set<String> existingRecipientKeys() {
        Set<String> keys = new HashSet<>();
        mongoTemplate.getCollection("message_recipients")
                .find().projection(new org.bson.Document("messageId", 1).append("recipientUserId", 1))
                .batchSize(10000)
                .forEach(doc -> keys.add(doc.getLong("messageId") + ":" + doc.getLong("recipientUserId")));
        return keys;
    }

    private long backfillMessages(Set<Long> existing) {
        long lastId = 0L;
        long total = 0L;
        List<Map<String, Object>> rows;
        do {
            rows = jdbcTemplate.queryForList(MESSAGE_SQL, lastId);
            List<MongoMessage> docs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object dept = row.get("sender_department_id");
                Long senderId = longValue(row.get("sender_id"));
                long id = longValue(row.get("id"));
                if (existing.contains(id)) {
                    lastId = id;
                    continue;
                }
                docs.add(MongoMessage.builder()
                        .messageId(id)
                        .senderUserId(senderId)
                        .senderRole((String) row.get("sender_role"))
                        .senderDepartmentId(dept == null ? null : longValue(dept))
                        .title((String) row.get("title"))
                        .content((String) row.get("content"))
                        .messageType((String) row.get("message_type"))
                        .createdAt(asLocalDateTime(row.get("created_at")))
                        .updatedAt(asLocalDateTime(row.get("created_at")))
                        .build());
                lastId = id;
            }
            total += insertMissing(docs, MongoMessage.class, MESSAGE_BATCH,
                    MongoMessage::getMessageId, existing);
        } while (rows.size() == MESSAGE_BATCH);
        log.info("[MIGRATE] messages={}", total);
        return total;
    }

    private long backfillRecipients(Set<String> existing) {
        Map<String, ReactionRow> reactions = loadReactions();
        long lastId = 0L;
        long total = 0L;
        List<Map<String, Object>> rows;
        do {
            rows = jdbcTemplate.queryForList(RECIPIENT_SQL, lastId);
            List<MongoMessageRecipient> docs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long messageId = longValue(row.get("message_id"));
                Long recipientId = longValue(row.get("recipient_id"));
                if (existing.contains(messageId + ":" + recipientId)) {
                    lastId = longValue(row.get("id"));
                    continue;
                }
                ReactionRow reaction = reactions.get(key(messageId, recipientId));
                docs.add(MongoMessageRecipient.builder()
                        .messageId(messageId)
                        .recipientUserId(recipientId)
                        .deliveredAt(asLocalDateTime(row.get("delivered_at")))
                        .readAt(asLocalDateTime(row.get("read_at")))
                        .reaction(reaction == null ? null : reaction.reaction())
                        .reactionAt(reaction == null ? null : reaction.createdAt())
                        .createdAt(asLocalDateTime(row.get("created_at")))
                        .updatedAt(asLocalDateTime(row.get("updated_at")))
                        .build());
                lastId = longValue(row.get("id"));
            }
            total += insertMissing(docs, MongoMessageRecipient.class, RECIPIENT_BATCH,
                    d -> d.getMessageId() + ":" + d.getRecipientUserId(), existing);
        } while (rows.size() == RECIPIENT_BATCH);
        log.info("[MIGRATE] recipients={}", total);
        return total;
    }

    private Map<String, ReactionRow> loadReactions() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT message_id, user_id, reaction, created_at FROM message_reactions");
        Map<String, ReactionRow> reactions = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long messageId = longValue(row.get("message_id"));
            Long userId = longValue(row.get("user_id"));
            reactions.put(key(messageId, userId),
                    new ReactionRow((String) row.get("reaction"), asLocalDateTime(row.get("created_at"))));
        }
        log.info("[MIGRATE] reactions loaded={}", reactions.size());
        return reactions;
    }

    private long backfillThreads(Set<Long> existing) {
        long lastId = 0L;
        long total = 0L;
        List<Map<String, Object>> rows;
        do {
            rows = jdbcTemplate.queryForList(THREAD_SQL, lastId);
            List<MongoClarificationThread> docs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long id = longValue(row.get("id"));
                if (existing.contains(id)) {
                    lastId = id;
                    continue;
                }
                docs.add(MongoClarificationThread.builder()
                        .threadId(id)
                        .messageId(longValue(row.get("message_id")))
                        .requesterUserId(longValue(row.get("requester_id")))
                        .senderUserId(longValue(row.get("sender_id")))
                        .status((String) row.get("status"))
                        .createdAt(asLocalDateTime(row.get("created_at")))
                        .updatedAt(asLocalDateTime(row.get("updated_at")))
                        .build());
                lastId = id;
            }
            total += insertMissing(docs, MongoClarificationThread.class, THREAD_BATCH,
                    MongoClarificationThread::getThreadId, existing);
        } while (rows.size() == THREAD_BATCH);
        log.info("[MIGRATE] threads={}", total);
        return total;
    }

    private long backfillEntries(Set<Long> existing) {
        long lastId = 0L;
        long total = 0L;
        List<Map<String, Object>> rows;
        do {
            rows = jdbcTemplate.queryForList(ENTRY_SQL, lastId);
            List<MongoClarificationEntry> docs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long id = longValue(row.get("id"));
                if (existing.contains(id)) {
                    lastId = id;
                    continue;
                }
                docs.add(MongoClarificationEntry.builder()
                        .entryId(id)
                        .threadId(longValue(row.get("thread_id")))
                        .authorUserId(longValue(row.get("author_id")))
                        .content((String) row.get("content"))
                        .createdAt(asLocalDateTime(row.get("created_at")))
                        .build());
                lastId = id;
            }
            total += insertMissing(docs, MongoClarificationEntry.class, ENTRY_BATCH,
                    MongoClarificationEntry::getEntryId, existing);
        } while (rows.size() == ENTRY_BATCH);
        log.info("[MIGRATE] entries={}", total);
        return total;
    }

    private <T> long insertMissing(List<T> docs, Class<T> type, int chunkSize,
                                   Function<T, Object> keyOf, Set<?> existing) {
        if (docs.isEmpty()) {
            return 0L;
        }
        List<T> missing = docs.stream()
                .filter(d -> !existing.contains(keyOf.apply(d)))
                .toList();
        if (missing.isEmpty()) {
            return 0L;
        }
        for (int i = 0; i < missing.size(); i += chunkSize) {
            List<T> slice = missing.subList(i, Math.min(missing.size(), i + chunkSize));
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, type);
            for (T doc : slice) {
                bulk.insert(doc);
            }
            bulk.execute();
        }
        return missing.size();
    }

    private static String key(Object messageId, Object userId) {
        return messageId + ":" + userId;
    }

    private static String key(Long messageId, Long userId) {
        return messageId + ":" + userId;
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static LocalDateTime asLocalDateTime(Object value) {
        return value == null ? null : ((Timestamp) value).toLocalDateTime();
    }
}