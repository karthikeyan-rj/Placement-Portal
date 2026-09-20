package com.college.placement.messaging.mongo.service;

import com.college.placement.messaging.mongo.document.MongoClarificationEntry;
import com.college.placement.messaging.mongo.document.MongoClarificationThread;
import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageRecipient;
import com.college.placement.messaging.mongo.repository.MongoClarificationEntryRepository;
import com.college.placement.messaging.mongo.repository.MongoClarificationThreadRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRecipientRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.mongodb.enabled", "app.mongodb.validate-enabled"},
        havingValue = "true",
        matchIfMissing = false)
public class MongoValidationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final MongoMessageRepository messageRepository;
    private final MongoMessageRecipientRepository recipientRepository;
    private final MongoClarificationThreadRepository threadRepository;
    private final MongoClarificationEntryRepository entryRepository;

    @Override
    public void run(String... args) {
        log.info("[VALIDATE] START");
        validateCounts();
        validateMessages();
        validateRecipients();
        validateThreads();
        validateEntries();
        validateSpecialMarkers();
        log.info("[VALIDATE] DONE");
    }

    private void validateCounts() {
        long pgMessages = scalar("SELECT COUNT(*) FROM messages");
        long pgRecipients = scalar("SELECT COUNT(*) FROM message_recipients");
        long pgReactions = scalar("SELECT COUNT(*) FROM message_reactions");
        long pgThreads = scalar("SELECT COUNT(*) FROM clarification_threads");
        long pgEntries = scalar("SELECT COUNT(*) FROM clarification_entries");

        long mongoMessages = mongoTemplate.getCollection("messages").countDocuments();
        long mongoRecipients = mongoTemplate.getCollection("message_recipients").countDocuments();
        long mongoReactions = mongoTemplate.getCollection("message_recipients")
                .countDocuments(new Document("reaction", new Document("$ne", null)));
        long mongoThreads = mongoTemplate.getCollection("clarification_threads").countDocuments();
        long mongoEntries = mongoTemplate.getCollection("clarification_entries").countDocuments();

        line("messages", pgMessages, mongoMessages);
        line("recipients", pgRecipients, mongoRecipients);
        line("reactions", pgReactions, mongoReactions);
        line("threads", pgThreads, mongoThreads);
        line("entries", pgEntries, mongoEntries);
    }

    private void validateMessages() {
        List<Long> pgIds = jdbcTemplate.queryForList("SELECT id FROM messages ORDER BY id", Long.class);
        List<Long> mongoIds = mongoTemplate.getCollection("messages")
                .distinct("messageId", Long.class).into(new ArrayList<>());
        Set<Long> pgSet = new HashSet<>(pgIds);
        Set<Long> mongoSet = new HashSet<>(mongoIds);
        jdkSetDiff("MESSAGE_IDS", pgSet, mongoSet);

        long missing = 0;
        long extra = 0;
        List<Long> mismatches = new ArrayList<>();
        for (Long messageId : pgIds) {
            Map<String, Object> pg = jdbcTemplate.queryForMap(
                    "SELECT sender_id AS sender_id, title, content, message_type AS message_type, created_at AS created_at "
                            + "FROM messages WHERE id = ?", messageId);
            MongoMessage mongo = messageRepository.findByMessageId(messageId).orElse(null);
            if (mongo == null) {
                missing++;
                continue;
            }
            boolean match = longValue(pg.get("sender_id")) == mongo.getSenderUserId()
                    && String.valueOf(pg.get("title")).equals(mongo.getTitle())
                    && String.valueOf(pg.get("content")).equals(mongo.getContent())
                    && String.valueOf(pg.get("message_type")).equals(mongo.getMessageType())
                    && toMillis(pg.get("created_at")) == toMillis(mongo.getCreatedAt());
            if (!match) {
                mismatches.add(messageId);
            }
        }
        if (extra == 0 && missing == 0 && mismatches.isEmpty()) {
            log.info("[VALIDATE] MESSAGE_FIELDS PASS messages={}", pgIds.size());
        } else {
            log.warn("[VALIDATE] MESSAGE_FIELDS FAIL missing={} extra={} fieldMismatch={} sample={}",
                    missing, extra, mismatches.size(), mismatches.subList(0, Math.min(5, mismatches.size())));
        }
    }

    private void validateRecipients() {
        long missingRecipients = 0;
        long extraRecipients = 0;
        long duplicateRecipients = 0;
        long readStateMismatch = 0;
        long reactionMismatch = 0;
        long deliveryMismatch = 0;
        int signatureMismatch = 0;
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM messages ORDER BY id", Long.class);

        for (Long messageId : ids) {
            List<Map<String, Object>> pgRows = jdbcTemplate.queryForList(
                    "SELECT recipient_id AS recipient_id, delivered_at AS delivered_at, read_at AS read_at "
                            + "FROM message_recipients WHERE message_id = ? ORDER BY recipient_id", messageId);
            Map<String, Object> pgReactions = new HashMap<>();
            for (Map<String, Object> reactionRow : jdbcTemplate.queryForList(
                    "SELECT user_id AS user_id, reaction FROM message_reactions WHERE message_id = ?", messageId)) {
                pgReactions.put(String.valueOf(reactionRow.get("user_id")), reactionRow.get("reaction"));
            }
            List<MongoMessageRecipient> mongoRows = recipientRepository.findByMessageId(messageId);

            List<String> pgLines = new ArrayList<>();
            for (Map<String, Object> row : pgRows) {
                String userId = String.valueOf(row.get("recipient_id"));
                String read = row.get("read_at") == null ? "0" : "1";
                Object reaction = pgReactions.get(userId);
                pgLines.add(userId + ":" + read + ":" + (reaction == null ? "" : reaction));
            }
            List<String> mongoLines = new ArrayList<>();
            for (MongoMessageRecipient row : mongoRows) {
                String read = row.getReadAt() == null ? "0" : "1";
                String reaction = row.getReaction() == null ? "" : row.getReaction();
                mongoLines.add(row.getRecipientUserId() + ":" + read + ":" + reaction);
            }
            pgLines.sort(String::compareTo);
            mongoLines.sort(String::compareTo);
            if (!pgLines.equals(mongoLines)) {
                signatureMismatch++;
            }

            Map<Long, MongoMessageRecipient> byUser = new HashMap<>();
            Map<Long, Integer> countPerUser = new HashMap<>();
            for (MongoMessageRecipient row : mongoRows) {
                byUser.put(row.getRecipientUserId(), row);
                countPerUser.merge(row.getRecipientUserId(), 1, Integer::sum);
            }
            for (int count : countPerUser.values()) {
                if (count > 1) {
                    duplicateRecipients += count - 1;
                }
            }
            Set<Long> pgUserSet = new HashSet<>();
            for (Map<String, Object> row : pgRows) {
                pgUserSet.add(longValue(row.get("recipient_id")));
            }
            for (Long userId : pgUserSet) {
                MongoMessageRecipient mongo = byUser.get(userId);
                if (mongo == null) {
                    missingRecipients++;
                    continue;
                }
                String read = String.valueOf(byUser.get(userId).getReadAt() == null ? "0" : "1");
                String readExpected = pgRows.stream()
                        .filter(r -> longValue(r.get("recipient_id")) == userId)
                        .findFirst()
                        .map(r -> r.get("read_at") == null ? "0" : "1")
                        .orElse("0");
                if (!read.equals(readExpected)) {
                    readStateMismatch++;
                }
                Object pgReaction = pgReactions.get(String.valueOf(userId));
                String expected = pgReaction == null ? "" : String.valueOf(pgReaction);
                String actual = mongo.getReaction() == null ? "" : mongo.getReaction();
                if (!expected.equals(actual)) {
                    reactionMismatch++;
                }
                boolean pgDelivered = pgRows.stream()
                        .filter(r -> longValue(r.get("recipient_id")) == userId)
                        .findFirst()
                        .map(r -> r.get("delivered_at") != null)
                        .orElse(false);
                if (pgDelivered != (mongo.getDeliveredAt() != null)) {
                    deliveryMismatch++;
                }
            }
            for (MongoMessageRecipient mongo : mongoRows) {
                if (!pgUserSet.contains(mongo.getRecipientUserId())) {
                    extraRecipients++;
                }
            }
        }

        dq("RECIPIENT_MISSING", missingRecipients);
        dq("RECIPIENT_EXTRA", extraRecipients);
        dq("RECIPIENT_DUPLICATES", duplicateRecipients);
        dq("READ_STATE_MISMATCH", readStateMismatch);
        dq("REACTION_MISMATCH", reactionMismatch);
        dq("DELIVERY_MISMATCH", deliveryMismatch);
        dq("RECIPIENT_SIGNATURE_MISMATCH", signatureMismatch);
    }

    private void validateThreads() {
        List<Long> pgIds = jdbcTemplate.queryForList("SELECT id FROM clarification_threads ORDER BY id", Long.class);
        long missing = 0;
        long extra = 0;
        long fieldMismatch = 0;
        for (Long threadId : pgIds) {
            Map<String, Object> pg = jdbcTemplate.queryForMap(
                    "SELECT message_id AS message_id, requester_id AS requester_id, sender_id AS sender_id, "
                            + "status, created_at AS created_at, updated_at AS updated_at "
                            + "FROM clarification_threads WHERE id = ?", threadId);
            MongoClarificationThread mongo = threadRepository.findByThreadId(threadId).orElse(null);
            if (mongo == null) {
                missing++;
                continue;
            }
            boolean match = longValue(pg.get("message_id")) == mongo.getMessageId()
                    && longValue(pg.get("requester_id")) == mongo.getRequesterUserId()
                    && longValue(pg.get("sender_id")) == mongo.getSenderUserId()
                    && String.valueOf(pg.get("status")).equals(mongo.getStatus())
                    && toMillis(pg.get("created_at")) == toMillis(mongo.getCreatedAt())
                    && toMillis(pg.get("updated_at")) == toMillis(mongo.getUpdatedAt());
            if (!match) {
                fieldMismatch++;
            }
        }
        Set<Long> mongoIds = new HashSet<>(
                mongoTemplate.getCollection("clarification_threads").distinct("threadId", Long.class)
                        .into(new ArrayList<>()));
        for (Long threadId : mongoIds) {
            if (!new HashSet<>(pgIds).contains(threadId)) {
                extra++;
            }
        }
        combine("THREAD", pgIds.size(), missing, extra, fieldMismatch);
    }

    private void validateEntries() {
        List<Map<String, Object>> pgRows = jdbcTemplate.queryForList(
                "SELECT id AS id, thread_id AS thread_id, author_id AS author_id, content, created_at AS created_at "
                        + "FROM clarification_entries ORDER BY id");
        long missing = 0;
        long extra = 0;
        long fieldMismatch = 0;
        Set<Long> mongoIds = new HashSet<>(
                mongoTemplate.getCollection("clarification_entries").distinct("entryId", Long.class)
                        .into(new ArrayList<>()));
        for (Map<String, Object> row : pgRows) {
            Long entryId = longValue(row.get("id"));
            MongoClarificationEntry mongo = entryRepository.findByEntryId(entryId).orElse(null);
            if (mongo == null) {
                missing++;
                continue;
            }
            boolean match = longValue(row.get("thread_id")) == mongo.getThreadId()
                    && longValue(row.get("author_id")) == mongo.getAuthorUserId()
                    && String.valueOf(row.get("content")).equals(mongo.getContent())
                    && toMillis(row.get("created_at")) == toMillis(mongo.getCreatedAt());
            if (!match) {
                fieldMismatch++;
            }
        }
        for (Long entryId : mongoIds) {
            if (!pgRows.stream().anyMatch(r -> longValue(r.get("id")) == entryId)) {
                extra++;
            }
        }
        combine("ENTRY", pgRows.size(), missing, extra, fieldMismatch);
    }

    private void validateSpecialMarkers() {
        checkMarker(22L, "MSG-22-LARGE-BROADCAST");
        checkMarker(38L, "MSG-38-ACK-200/50");
        checkMarker(140L, "MSG-140-BULK-6E-PERSIST");
    }

    private void checkMarker(Long messageId, String label) {
        List<Long> pgUsers = jdbcTemplate.queryForList(
                "SELECT recipient_id FROM message_recipients WHERE message_id = ? ORDER BY recipient_id",
                Long.class, messageId);
        long pgRecipients = pgUsers.size();
        long mongoRecipients = mongoTemplate.getCollection("message_recipients")
                .countDocuments(new Document("messageId", messageId));
        Set<Long> mongoUsers = new TreeSet<>(mongoTemplate.getCollection("message_recipients")
                .distinct("recipientUserId", new Document("messageId", messageId), Long.class)
                .into(new ArrayList<>()));
        Set<Long> pgUserSet = new HashSet<>(pgUsers);
        long missing = pgUsers.stream().filter(u -> !mongoUsers.contains(u)).count();
        long extra = mongoUsers.stream().filter(u -> !pgUserSet.contains(u)).count();
        long expectedUp = scalar("SELECT COUNT(*) FROM message_reactions WHERE message_id = ? AND reaction = 'UPVOTE'", messageId);
        long expectedDown = scalar("SELECT COUNT(*) FROM message_reactions WHERE message_id = ? AND reaction = 'DOWNVOTE'", messageId);
        long mongoUp = mongoTemplate.getCollection("message_recipients")
                .countDocuments(new Document("messageId", messageId).append("reaction", "UPVOTE"));
        long mongoDown = mongoTemplate.getCollection("message_recipients")
                .countDocuments(new Document("messageId", messageId).append("reaction", "DOWNVOTE"));
        boolean pass = pgRecipients == mongoRecipients
                && expectedUp == mongoUp && expectedDown == mongoDown
                && missing == 0 && extra == 0;
        log.info("[VALIDATE] {} pass={} pgRecipients={} mongoRecipients={} up={}/{} down={}/{} missingIds={} extraIds={}",
                label, pass, pgRecipients, mongoRecipients, expectedUp, mongoUp, expectedDown, mongoDown, missing, extra);
    }

    private void line(String label, long pg, long mongo) {
        boolean pass = pg == mongo;
        log.info("[VALIDATE] COUNT_{} pass={} postgres={} mongo={}", label, pass, pg, mongo);
    }

    private void dq(String label, long value) {
        boolean pass = value == 0;
        log.info("[VALIDATE] {} pass={} count={}", label, pass, value);
    }

    private void combine(String label, long total, long missing, long extra, long fieldMismatch) {
        boolean pass = missing == 0 && extra == 0 && fieldMismatch == 0;
        log.info("[VALIDATE] {}_EXACT pass={} total={} missing={} extra={} fieldMismatch={}", label, pass, total, missing, extra, fieldMismatch);
    }

    private void jdkSetDiff(String label, Set<Long> pg, Set<Long> mongo) {
        Set<Long> missing = new HashSet<>(pg);
        missing.removeAll(mongo);
        Set<Long> extra = new HashSet<>(mongo);
        extra.removeAll(pg);
        boolean pass = missing.isEmpty() && extra.isEmpty();
        log.info("[VALIDATE] {} pass={} missing={} extra={}", label, pass, missing.size(), extra.size());
        if (!pass) {
            log.warn("[VALIDATE] {} sample missing={} extra={}", label,
                    missing.stream().sorted().limit(5).toList(),
                    extra.stream().sorted().limit(5).toList());
        }
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static long toMillis(Object value) {
        if (value == null) {
            return 0;
        }
        LocalDateTime ts = value instanceof LocalDateTime
                ? (LocalDateTime) value
                : ((Timestamp) value).toLocalDateTime();
        return ts.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}