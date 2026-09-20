package com.college.placement.messaging.mongo.service;

import com.college.placement.messaging.mongo.document.MongoClarificationEntry;
import com.college.placement.messaging.mongo.document.MongoClarificationThread;
import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageRecipient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reverse migration tool: copies messaging data from MongoDB back into PostgreSQL.
 * Gated by app.mongodb.rollback-enabled (env MIGRATE_MESSAGES_MONGO_TO_POSTGRES), default OFF.
 * Dry-run mode (app.mongodb.rollback-dry-run, env MONGO_ROLLBACK_DRY_RUN, default true) reports
 * inserted/updated/unchanged/conflicted without writing. Never truncates PostgreSQL and only touches
 * the five messaging tables (messages, message_recipients, message_reactions,
 * clarification_threads, clarification_entries). Comparison is structure/state-based
 * (content, reaction, status, read/delivered flags); timestamps are never used for comparison and
 * existing rows never have their timestamps rewritten.
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.mongodb.enabled", "app.mongodb.rollback-enabled"},
        havingValue = "true",
        matchIfMissing = false)
public class MongoRollbackRunner implements CommandLineRunner {

    private static final int CHUNK = 500;

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${app.mongodb.rollback-dry-run:true}")
    private boolean dryRun;

    private static class Titles {
        final int inserted;
        final int updated;
        final int unchanged;
        final int conflicted;
        Titles(int inserted, int updated, int unchanged, int conflicted) {
            this.inserted = inserted;
            this.updated = updated;
            this.unchanged = unchanged;
            this.conflicted = conflicted;
        }
    }

    @Override
    public void run(String... args) {
        log.info("[ROLLBACK] START dryRun={}", dryRun);
        Titles messages = rollbackMessages();
        Titles recipients = rollbackRecipients();
        Titles reactions = rollbackReactions();
        Titles threads = rollbackThreads();
        Titles entries = rollbackEntries();
        log.info("[ROLLBACK] messages inserted={} updated={} unchanged={} conflicted={}",
                messages.inserted, messages.updated, messages.unchanged, messages.conflicted);
        log.info("[ROLLBACK] recipients inserted={} updated={} unchanged={} conflicted={}",
                recipients.inserted, recipients.updated, recipients.unchanged, recipients.conflicted);
        log.info("[ROLLBACK] reactions inserted={} updated={} unchanged={} conflicted={}",
                reactions.inserted, reactions.updated, reactions.unchanged, reactions.conflicted);
        log.info("[ROLLBACK] threads inserted={} updated={} unchanged={} conflicted={}",
                threads.inserted, threads.updated, threads.unchanged, threads.conflicted);
        log.info("[ROLLBACK] entries inserted={} updated={} unchanged={} conflicted={}",
                entries.inserted, entries.updated, entries.unchanged, entries.conflicted);
        log.info("[ROLLBACK] DONE dryRun={} totalInserted={} totalUpdated={} totalChanged={} totalConflicted={}",
                dryRun, messages.inserted + recipients.inserted + reactions.inserted + threads.inserted + entries.inserted,
                messages.updated + recipients.updated + reactions.updated + threads.updated + entries.updated,
                messages.inserted + recipients.inserted + reactions.inserted + threads.inserted + entries.inserted
                        + messages.updated + recipients.updated + reactions.updated + threads.updated + entries.updated,
                messages.conflicted + recipients.conflicted + reactions.conflicted + threads.conflicted + entries.conflicted);
    }

    private Titles rollbackMessages() {
        Map<Long, MongoMessage> docs = new HashMap<>();
        for (MongoMessage doc : mongoTemplate.findAll(MongoMessage.class)) {
            docs.put(doc.getMessageId(), doc);
        }
        Set<Long> pgIds = new HashSet<>(jdbcTemplate.queryForList("SELECT id FROM messages", Long.class));
        Map<Long, Map<String, Object>> pgRows = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, sender_id, title, content, message_type FROM messages")) {
            pgRows.put(((Number) row.get("id")).longValue(), row);
        }
        List<Long> ids = new ArrayList<>(docs.keySet());
        ids.sort(Long::compareTo);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<Object[]> insertBatch = new ArrayList<>();
        for (Long id : ids) {
            MongoMessage doc = docs.get(id);
            Map<String, Object> pg = pgRows.get(id);
            if (pg == null) {
                inserted++;
                insertBatch.add(new Object[]{id, doc.getSenderUserId(), doc.getTitle(), doc.getContent(),
                        doc.getMessageType(), toTs(doc.getCreatedAt())});
                if (insertBatch.size() >= CHUNK) {
                    bulkInsert("INSERT INTO messages (id, sender_id, title, content, message_type, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", insertBatch);
                }
                continue;
            }
            boolean same = eq(pg.get("title"), doc.getTitle()) && eq(pg.get("content"), doc.getContent())
                    && eq(pg.get("message_type"), doc.getMessageType())
                    && eqNum(pg.get("sender_id"), doc.getSenderUserId());
            if (same) {
                unchanged++;
            } else {
                conflicted++;
            }
        }
        bulkInsert("INSERT INTO messages (id, sender_id, title, content, message_type, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", insertBatch);
        return new Titles(inserted, updated, unchanged, conflicted);
    }

    private Titles rollbackRecipients() {
        List<MongoMessageRecipient> docs = mongoTemplate.findAll(MongoMessageRecipient.class);
        Set<String> pgKeys = new HashSet<>();
        Map<String, Map<String, Object>> pgRows = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT message_id, recipient_id, delivered_at, read_at FROM message_recipients")) {
            String key = key(row.get("message_id"), row.get("recipient_id"));
            pgKeys.add(key);
            pgRows.put(key, row);
        }
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<Object[]> insertBatch = new ArrayList<>();
        List<Object[]> updateBatch = new ArrayList<>();
        for (MongoMessageRecipient doc : docs) {
            String key = key(doc.getMessageId(), doc.getRecipientUserId());
            Map<String, Object> pg = pgRows.get(key);
            if (pg == null) {
                inserted++;
                insertBatch.add(new Object[]{doc.getMessageId(), doc.getRecipientUserId(),
                        toTs(doc.getDeliveredAt()), toTs(doc.getReadAt()), toTs(doc.getCreatedAt()), toTs(doc.getUpdatedAt())});
                if (insertBatch.size() >= CHUNK) {
                    bulkInsert("INSERT INTO message_recipients (message_id, recipient_id, delivered_at, read_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", insertBatch);
                }
                continue;
            }
            boolean deliveredSame = bothSetOrNull(pg.get("delivered_at"), doc.getDeliveredAt());
            boolean readSame = bothSetOrNull(pg.get("read_at"), doc.getReadAt());
            if (deliveredSame && readSame) {
                unchanged++;
            } else {
                updated++;
                updateBatch.add(new Object[]{toTs(doc.getDeliveredAt()), toTs(doc.getReadAt()), toTs(doc.getUpdatedAt()),
                        doc.getMessageId(), doc.getRecipientUserId()});
                if (updateBatch.size() >= CHUNK) {
                    bulkUpdate("UPDATE message_recipients SET delivered_at = ?, read_at = ?, updated_at = ? "
                            + "WHERE message_id = ? AND recipient_id = ?", updateBatch);
                }
            }
        }
        bulkInsert("INSERT INTO message_recipients (message_id, recipient_id, delivered_at, read_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", insertBatch);
        bulkUpdate("UPDATE message_recipients SET delivered_at = ?, read_at = ?, updated_at = ? "
                + "WHERE message_id = ? AND recipient_id = ?", updateBatch);
        return new Titles(inserted, updated, unchanged, conflicted);
    }

    private Titles rollbackReactions() {
        List<MongoMessageRecipient> docs = mongoTemplate.findAll(MongoMessageRecipient.class);
        Map<String, String> pgReactions = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT message_id, user_id, reaction FROM message_reactions")) {
            pgReactions.put(key(row.get("message_id"), row.get("user_id")), String.valueOf(row.get("reaction")));
        }
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<Object[]> insertBatch = new ArrayList<>();
        for (MongoMessageRecipient doc : docs) {
            if (doc.getReaction() == null) {
                continue;
            }
            String key = key(doc.getMessageId(), doc.getRecipientUserId());
            String pgReaction = pgReactions.get(key);
            if (pgReaction == null) {
                inserted++;
                insertBatch.add(new Object[]{doc.getMessageId(), doc.getRecipientUserId(), doc.getReaction(),
                        toTs(doc.getReactionAt())});
                if (insertBatch.size() >= CHUNK) {
                    bulkInsert("INSERT INTO message_reactions (message_id, user_id, reaction, created_at) "
                            + "VALUES (?, ?, ?, ?)", insertBatch);
                }
            } else if (pgReaction.equals(doc.getReaction())) {
                unchanged++;
            } else {
                conflicted++;
            }
        }
        bulkInsert("INSERT INTO message_reactions (message_id, user_id, reaction, created_at) "
                + "VALUES (?, ?, ?, ?)", insertBatch);
        return new Titles(inserted, updated, unchanged, conflicted);
    }

    private Titles rollbackThreads() {
        Map<Long, MongoClarificationThread> docs = new HashMap<>();
        for (MongoClarificationThread doc : mongoTemplate.findAll(MongoClarificationThread.class)) {
            docs.put(doc.getThreadId(), doc);
        }
        Map<Long, Map<String, Object>> pgRows = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, message_id, requester_id, sender_id, status FROM clarification_threads")) {
            pgRows.put(((Number) row.get("id")).longValue(), row);
        }
        List<Long> ids = new ArrayList<>(docs.keySet());
        ids.sort(Long::compareTo);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<Object[]> insertBatch = new ArrayList<>();
        List<Object[]> updateBatch = new ArrayList<>();
        for (Long id : ids) {
            MongoClarificationThread doc = docs.get(id);
            Map<String, Object> pg = pgRows.get(id);
            if (pg == null) {
                inserted++;
                insertBatch.add(new Object[]{id, doc.getMessageId(), doc.getRequesterUserId(), doc.getSenderUserId(),
                        doc.getStatus(), toTs(doc.getCreatedAt()), toTs(doc.getUpdatedAt())});
                if (insertBatch.size() >= CHUNK) {
                    bulkInsert("INSERT INTO clarification_threads (id, message_id, requester_id, sender_id, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", insertBatch);
                }
                continue;
            }
            boolean baseSame = eqNum(pg.get("message_id"), doc.getMessageId())
                    && eqNum(pg.get("requester_id"), doc.getRequesterUserId())
                    && eqNum(pg.get("sender_id"), doc.getSenderUserId());
            if (!baseSame) {
                conflicted++;
            } else if (eq(pg.get("status"), doc.getStatus())) {
                unchanged++;
            } else {
                updated++;
                updateBatch.add(new Object[]{doc.getStatus(), id});
                if (updateBatch.size() >= CHUNK) {
                    bulkUpdate("UPDATE clarification_threads SET status = ? WHERE id = ?", updateBatch);
                }
            }
        }
        bulkInsert("INSERT INTO clarification_threads (id, message_id, requester_id, sender_id, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", insertBatch);
        bulkUpdate("UPDATE clarification_threads SET status = ? WHERE id = ?", updateBatch);
        return new Titles(inserted, updated, unchanged, conflicted);
    }

    private Titles rollbackEntries() {
        Map<Long, MongoClarificationEntry> docs = new HashMap<>();
        for (MongoClarificationEntry doc : mongoTemplate.findAll(MongoClarificationEntry.class)) {
            docs.put(doc.getEntryId(), doc);
        }
        Map<Long, Map<String, Object>> pgRows = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, thread_id, author_id, content FROM clarification_entries")) {
            pgRows.put(((Number) row.get("id")).longValue(), row);
        }
        List<Long> ids = new ArrayList<>(docs.keySet());
        ids.sort(Long::compareTo);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int conflicted = 0;
        List<Object[]> insertBatch = new ArrayList<>();
        for (Long id : ids) {
            MongoClarificationEntry doc = docs.get(id);
            Map<String, Object> pg = pgRows.get(id);
            if (pg == null) {
                inserted++;
                insertBatch.add(new Object[]{id, doc.getThreadId(), doc.getAuthorUserId(), doc.getContent(),
                        toTs(doc.getCreatedAt())});
                if (insertBatch.size() >= CHUNK) {
                    bulkInsert("INSERT INTO clarification_entries (id, thread_id, author_id, content, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)", insertBatch);
                }
                continue;
            }
            boolean same = eqNum(pg.get("thread_id"), doc.getThreadId())
                    && eqNum(pg.get("author_id"), doc.getAuthorUserId())
                    && eq(pg.get("content"), doc.getContent());
            if (same) {
                unchanged++;
            } else {
                conflicted++;
            }
        }
        bulkInsert("INSERT INTO clarification_entries (id, thread_id, author_id, content, created_at) "
                + "VALUES (?, ?, ?, ?, ?)", insertBatch);
        return new Titles(inserted, updated, unchanged, conflicted);
    }

    private void bulkInsert(String sql, List<Object[]> batch) {
        if (batch.isEmpty() || dryRun) {
            batch.clear();
            return;
        }
        jdbcTemplate.batchUpdate(sql, batch);
        batch.clear();
    }

    private void bulkUpdate(String sql, List<Object[]> batch) {
        if (batch.isEmpty() || dryRun) {
            batch.clear();
            return;
        }
        jdbcTemplate.batchUpdate(sql, batch);
        batch.clear();
    }

    private static String key(Object a, Object b) {
        return a + ":" + b;
    }

    private static boolean eq(Object a, Object b) {
        if (a == null) {
            return b == null;
        }
        return b != null && String.valueOf(a).equals(String.valueOf(b));
    }

    private static boolean eqNum(Object a, Long b) {
        if (a == null) {
            return b == null;
        }
        return b != null && ((Number) a).longValue() == b;
    }

    private static boolean bothSetOrNull(Object pg, LocalDateTime mongo) {
        return (pg == null) == (mongo == null);
    }

    private static Timestamp toTs(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Timestamp.valueOf(LocalDateTime.ofInstant(value.toInstant(ZoneOffset.UTC), ZoneId.systemDefault()));
    }
}