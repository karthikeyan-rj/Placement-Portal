package com.college.placement.messaging.mongo.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {"app.mongodb.enabled", "app.mongodb.perf-enabled"},
        havingValue = "true",
        matchIfMissing = false)
public class MongoPerfProbe implements CommandLineRunner {

    private static final int ROUNDS = 25;
    private static final int WARMUP = 3;

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final MongoMessageRepository messageRepository;
    private final MongoMessageRecipientRepository recipientRepository;
    private final MongoClarificationThreadRepository threadRepository;
    private final MongoClarificationEntryRepository entryRepository;

    @Override
    public void run(String... args) {
        long messageId = 22L;
        Long inboxUser = top("SELECT recipient_id FROM message_recipients GROUP BY recipient_id ORDER BY COUNT(*) DESC LIMIT 1");
        Long sentUser = top("SELECT sender_id FROM messages GROUP BY sender_id ORDER BY COUNT(*) DESC LIMIT 1");
        Long clarifySender = top("SELECT sender_id FROM clarification_threads GROUP BY sender_id ORDER BY COUNT(*) DESC LIMIT 1");
        Long maxEntryThread = top("SELECT thread_id FROM clarification_entries GROUP BY thread_id ORDER BY COUNT(*) DESC LIMIT 1");

        log.info("[PERF] params messageId={} inboxUser={} sentUser={} clarifySender={} maxEntryThread={}",
                messageId, inboxUser, sentUser, clarifySender, maxEntryThread);

        List<Long> messageById = measure(n -> {
            messageRepository.findByMessageId(messageId);
        });
        List<Long> inbox = measure(n -> {
            recipientRepository.findByRecipientUserIdOrderByCreatedAtDesc(inboxUser, PageRequest.of(0, 20));
        });
        List<Long> sent = measure(n -> {
            messageRepository.findBySenderUserIdOrderByCreatedAtDesc(sentUser, PageRequest.of(0, 20));
        });
        List<Long> analytics = measure(n -> {
            aggregateAnalytics(messageId);
        });
        List<Long> clarificationList = measure(n -> {
            threadRepository.findBySenderUserIdOrderByUpdatedAtDesc(clarifySender, PageRequest.of(0, 20));
        });
        List<Long> threadEntries = measure(n -> {
            entryRepository.findByThreadIdOrderByCreatedAtAsc(maxEntryThread);
        });

        report("messageById", messageById);
        report("inbox", inbox);
        report("sent", sent);
        report("analytics", analytics);
        report("clarificationList", clarificationList);
        report("threadEntries", threadEntries);
        log.info("[PERF] DONE");
    }

    private void aggregateAnalytics(Long messageId) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", messageId)),
                new Document("$group", new Document("_id", null)
                        .append("total", new Document("$sum", 1))
                        .append("delivered", new Document("$sum",
                                new Document("$cond", java.util.Arrays.asList(
                                        new Document("$ne", java.util.Arrays.asList("$deliveredAt", null)), 1, 0))))
                        .append("read", new Document("$sum",
                                new Document("$cond", java.util.Arrays.asList(
                                        new Document("$ne", java.util.Arrays.asList("$readAt", null)), 1, 0))))
                        .append("upvotes", new Document("$sum",
                                new Document("$cond", List.of(
                                        new Document("$eq", List.of("$reaction", "UPVOTE")), 1, 0))))
                        .append("downvotes", new Document("$sum",
                                new Document("$cond", List.of(
                                        new Document("$eq", List.of("$reaction", "DOWNVOTE")), 1, 0))))));
        mongoTemplate.getCollection("message_recipients").aggregate(pipeline).first();
    }

    private List<Long> measure(java.util.function.LongConsumer op) {
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < WARMUP; i++) {
            long start = System.nanoTime();
            op.accept(i);
            samples.add((System.nanoTime() - start) / 1_000_000);
        }
        samples.clear();
        for (int i = 0; i < ROUNDS; i++) {
            long start = System.nanoTime();
            op.accept(i);
            samples.add((System.nanoTime() - start) / 1_000_000);
        }
        return samples;
    }

    private void report(String label, List<Long> samples) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.naturalOrder());
        long median = sorted.get(sorted.size() / 2);
        long p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        log.info("[PERF] {} median={}ms p95={}ms rounds={}", label, median, p95, samples.size());
    }

    private Long top(String sql) {
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class);
        return rows.isEmpty() ? null : rows.get(0);
    }
}