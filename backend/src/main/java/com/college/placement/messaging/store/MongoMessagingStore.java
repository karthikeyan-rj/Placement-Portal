package com.college.placement.messaging.store;

import com.college.placement.common.enums.MessageReactionType;
import com.college.placement.common.enums.MessageType;
import com.college.placement.messaging.ClarificationStatus;
import com.college.placement.messaging.mongo.document.MongoClarificationEntry;
import com.college.placement.messaging.mongo.document.MongoClarificationThread;
import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageRecipient;
import com.college.placement.messaging.mongo.repository.MongoClarificationEntryRepository;
import com.college.placement.messaging.mongo.repository.MongoClarificationThreadRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRecipientRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRepository;
import com.college.placement.messaging.mongo.service.MongoSequenceService;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.storage", havingValue = "mongo", matchIfMissing = false)
public class MongoMessagingStore implements MessagingStore {

    private final MongoTemplate mongoTemplate;
    private final MongoSequenceService sequenceService;
    private final MongoMessageRepository messageRepository;
    private final MongoMessageRecipientRepository recipientRepository;
    private final MongoClarificationThreadRepository threadRepository;
    private final MongoClarificationEntryRepository entryRepository;
    private final UserRepository userRepository;

    @PostConstruct
    void logStorageMode() {
        log.info("[MESSAGING] activeStorage=mongo");
    }

    @Override
    public StoredMessage createMessage(User sender, String title, String content, MessageType type,
                                       List<User> recipients, LocalDateTime deliveredAt) {
        long messageId = sequenceService.next("message");
        LocalDateTime created = deliveredAt != null ? deliveredAt : LocalDateTime.now();
        MongoMessage message = MongoMessage.builder()
                .messageId(messageId)
                .senderUserId(sender.getId())
                .senderRole(sender.getRole().name())
                .senderDepartmentId(sender.getDepartment() != null ? sender.getDepartment().getId() : null)
                .title(title)
                .content(content)
                .messageType(type.name())
                .createdAt(created)
                .updatedAt(created)
                .build();
        try {
            messageRepository.save(message);
            List<MongoMessageRecipient> recipientDocs = recipients.stream()
                    .map(r -> MongoMessageRecipient.builder()
                            .messageId(messageId)
                            .recipientUserId(r.getId())
                            .deliveredAt(created)
                            .createdAt(created)
                            .updatedAt(created)
                            .build())
                    .toList();
            mongoTemplate.insert(recipientDocs, MongoMessageRecipient.class);
        } catch (RuntimeException ex) {
            cleanupMessage(messageId);
            throw ex;
        }
        return toStored(message);
    }

    private void cleanupMessage(long messageId) {
        try {
            mongoTemplate.remove(new Query(Criteria.where("messageId").is(messageId)), MongoMessageRecipient.class);
            mongoTemplate.remove(new Query(Criteria.where("messageId").is(messageId)), MongoMessage.class);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public StoredMessage getMessage(Long messageId) {
        return messageRepository.findByMessageId(messageId).map(this::toStored).orElse(null);
    }

    @Override
    public StoredPage<StoredMessage> sentBySender(Long senderUserId, Pageable pageable) {
        List<MongoMessage> content = messageRepository.findBySenderUserIdOrderByCreatedAtDesc(senderUserId, pageable);
        long total = messageRepository.countBySenderUserId(senderUserId);
        return new StoredPage<>(content.stream().map(this::toStored).toList(), total);
    }

    @Override
    public StoredPage<StoredMessage> receivedByUser(Long userId, Pageable pageable) {
        List<MongoMessageRecipient> recipients =
                recipientRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        long total = recipientRepository.countByRecipientUserId(userId);
        if (recipients.isEmpty()) {
            return new StoredPage<>(List.of(), total);
        }
        List<Long> messageIds = recipients.stream().map(MongoMessageRecipient::getMessageId).distinct().toList();
        Map<Long, MongoMessage> byId = messageRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.toMap(MongoMessage::getMessageId, m -> m, (a, b) -> a));
        List<StoredMessage> content = recipients.stream()
                .map(r -> byId.get(r.getMessageId()))
                .filter(m -> m != null)
                .map(this::toStored)
                .toList();
        return new StoredPage<>(content, total);
    }

    @Override
    public boolean isRecipient(Long messageId, Long userId) {
        return recipientRepository.existsByMessageIdAndRecipientUserId(messageId, userId);
    }

    @Override
    public void markRead(Long messageId, Long userId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("messageId").is(messageId)
                        .and("recipientUserId").is(userId)
                        .and("readAt").is(null)),
                new Update().set("readAt", LocalDateTime.now()).set("updatedAt", LocalDateTime.now()),
                MongoMessageRecipient.class);
    }

    @Override
    public void setReaction(Long messageId, Long userId, MessageReactionType reaction) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("messageId").is(messageId).and("recipientUserId").is(userId)),
                new Update().set("reaction", reaction.name())
                        .set("reactionAt", LocalDateTime.now())
                        .set("updatedAt", LocalDateTime.now()),
                MongoMessageRecipient.class);
    }

    @Override
    public MessageReactionType getMyReaction(Long messageId, Long userId) {
        MongoMessageRecipient recipient =
                recipientRepository.findByMessageIdAndRecipientUserId(messageId, userId).orElse(null);
        if (recipient == null || recipient.getReaction() == null) {
            return null;
        }
        try {
            return MessageReactionType.valueOf(recipient.getReaction());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public long analyticsTotal(Long messageId) {
        return recipientRepository.countByMessageId(messageId);
    }

    @Override
    public long analyticsDelivered(Long messageId) {
        return recipientRepository.countByMessageIdAndDeliveredAtNotNull(messageId);
    }

    @Override
    public long analyticsRead(Long messageId) {
        return recipientRepository.countByMessageIdAndReadAtNotNull(messageId);
    }

    @Override
    public long analyticsUpvotes(Long messageId) {
        return recipientRepository.countByMessageIdAndReaction(messageId, MessageReactionType.UPVOTE.name());
    }

    @Override
    public long analyticsDownvotes(Long messageId) {
        return recipientRepository.countByMessageIdAndReaction(messageId, MessageReactionType.DOWNVOTE.name());
    }

    @Override
    public List<MessageStatsRow> messageStats(Collection<Long> messageIds) {
        List<MessageStatsRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", new Document("$in", messageIds))),
                new Document("$group", new Document("_id", "$messageId")
                        .append("total", new Document("$sum", 1))
                        .append("delivered", new Document("$sum", new Document("$cond",
                                Arrays.asList(new Document("$eq",
                                        Arrays.asList(new Document("$type", "$deliveredAt"), "date")), 1, 0))))
                        .append("read", new Document("$sum", new Document("$cond",
                                Arrays.asList(new Document("$eq",
                                        Arrays.asList(new Document("$type", "$readAt"), "date")), 1, 0))))
                        .append("upvotes", new Document("$sum", new Document("$cond",
                                List.of(new Document("$eq", List.of("$reaction", "UPVOTE")), 1, 0))))
                        .append("downvotes", new Document("$sum", new Document("$cond",
                                List.of(new Document("$eq", List.of("$reaction", "DOWNVOTE")), 1, 0))))));
        for (Document doc : mongoTemplate.getCollection("message_recipients").aggregate(pipeline)) {
            rows.add(new MessageStatsRow(
                    doc.getLong("_id"),
                    toLong(doc.get("total")),
                    toLong(doc.get("delivered")),
                    toLong(doc.get("read")),
                    toLong(doc.get("upvotes")),
                    toLong(doc.get("downvotes"))));
        }
        return rows;
    }

    @Override
    public List<ReadFlagRow> readFlags(Collection<Long> messageIds, Long userId) {
        List<ReadFlagRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", new Document("$in", messageIds))
                        .append("recipientUserId", userId)),
                new Document("$project", new Document("messageId", 1)
                        .append("read", new Document("$cond",
                                Arrays.asList(new Document("$eq",
                                        Arrays.asList(new Document("$type", "$readAt"), "date")), true, false)))));
        for (Document doc : mongoTemplate.getCollection("message_recipients").aggregate(pipeline)) {
            rows.add(new ReadFlagRow(doc.getLong("messageId"), Boolean.TRUE.equals(doc.get("read"))));
        }
        return rows;
    }

    @Override
    public List<ReactionRow> myReactions(Collection<Long> messageIds, Long userId) {
        List<ReactionRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", new Document("$in", messageIds))
                        .append("recipientUserId", userId)
                        .append("reaction", new Document("$ne", null))),
                new Document("$project", new Document("messageId", 1).append("reaction", 1)));
        for (Document doc : mongoTemplate.getCollection("message_recipients").aggregate(pipeline)) {
            rows.add(new ReactionRow(doc.getLong("messageId"),
                    MessageReactionType.valueOf(doc.getString("reaction"))));
        }
        return rows;
    }

    @Override
    public List<RecipientExportRow> recipientsForExport(Long messageId) {
        List<MongoMessageRecipient> docs = recipientRepository.findByMessageId(messageId);
        if (docs.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = docs.stream().map(MongoMessageRecipient::getRecipientUserId).distinct().toList();
        Map<Long, User> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findUsersByIds(userIds)) {
                users.put(u.getId(), u);
            }
        }
        List<RecipientExportRow> rows = new ArrayList<>();
        for (MongoMessageRecipient r : docs) {
            User u = users.get(r.getRecipientUserId());
            rows.add(new RecipientExportRow(
                    r.getRecipientUserId(),
                    u != null ? u.getName() : null,
                    u != null && u.getDepartment() != null ? u.getDepartment().getId() : null,
                    u != null && u.getDepartment() != null ? u.getDepartment().getName() : null,
                    r.getDeliveredAt() != null,
                    r.getReadAt() != null,
                    r.getReaction() != null ? r.getReaction() : "NONE",
                    r.getCreatedAt()));
        }
        return rows;
    }

    @Override
    public StoredThread getClarificationThread(Long threadId) {
        return threadRepository.findByThreadId(threadId).map(this::toStored).orElse(null);
    }

    @Override
    public StoredThread getClarificationThread(Long messageId, Long requesterUserId) {
        return threadRepository.findByMessageIdAndRequesterUserId(messageId, requesterUserId)
                .map(this::toStored).orElse(null);
    }

    @Override
    public StoredThread createClarificationThread(Long messageId, Long requesterUserId, Long senderUserId) {
        long threadId = sequenceService.next("clarificationThread");
        LocalDateTime now = LocalDateTime.now();
        MongoClarificationThread thread = MongoClarificationThread.builder()
                .threadId(threadId)
                .messageId(messageId)
                .requesterUserId(requesterUserId)
                .senderUserId(senderUserId)
                .status(ClarificationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toStored(threadRepository.save(thread));
    }

    @Override
    public StoredThread updateClarificationThreadStatus(Long threadId, ClarificationStatus status) {
        MongoClarificationThread thread = threadRepository.findByThreadId(threadId).orElseThrow();
        thread.setStatus(status.name());
        thread.setUpdatedAt(LocalDateTime.now());
        return toStored(threadRepository.save(thread));
    }

    @Override
    public StoredPage<StoredThread> threadsForMessage(Long messageId, Pageable pageable) {
        List<MongoClarificationThread> content =
                threadRepository.findByMessageIdOrderByUpdatedAtDesc(messageId, pageable);
        long total = threadRepository.countByMessageId(messageId);
        return new StoredPage<>(toStoredList(content), total);
    }

    @Override
    public StoredPage<StoredThread> incomingThreads(Long senderUserId, Pageable pageable) {
        List<MongoClarificationThread> content =
                threadRepository.findBySenderUserIdOrderByUpdatedAtDesc(senderUserId, pageable);
        long total = threadRepository.countBySenderUserId(senderUserId);
        return new StoredPage<>(toStoredList(content), total);
    }

    @Override
    public long clarificationTotal(Long messageId) {
        return threadRepository.countByMessageId(messageId);
    }

    @Override
    public long clarificationOpen(Long messageId) {
        return threadRepository.countByMessageIdAndStatus(messageId, ClarificationStatus.OPEN.name());
    }

    @Override
    public List<ClarificationSummaryRow> clarificationSummaries(Collection<Long> messageIds) {
        List<ClarificationSummaryRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", new Document("$in", messageIds))),
                new Document("$group", new Document("_id", "$messageId")
                        .append("total", new Document("$sum", 1))
                        .append("open", new Document("$sum", new Document("$cond",
                                List.of(new Document("$eq", List.of("$status", "OPEN")), 1, 0))))));
        for (Document doc : mongoTemplate.getCollection("clarification_threads").aggregate(pipeline)) {
            rows.add(new ClarificationSummaryRow(
                    doc.getLong("_id"), toLong(doc.get("total")), toLong(doc.get("open"))));
        }
        return rows;
    }

    @Override
    public StoredEntry saveClarificationEntry(Long threadId, Long authorUserId, String content) {
        long entryId = sequenceService.next("clarificationEntry");
        MongoClarificationEntry entry = MongoClarificationEntry.builder()
                .entryId(entryId)
                .threadId(threadId)
                .authorUserId(authorUserId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        entry = entryRepository.save(entry);
        return new StoredEntry(entry.getEntryId(), entry.getAuthorUserId(), entry.getContent(), entry.getCreatedAt());
    }

    @Override
    public StoredPage<StoredEntry> entriesForThread(Long threadId, Pageable pageable) {
        List<MongoClarificationEntry> all = entryRepository.findByThreadIdOrderByCreatedAtAsc(threadId);
        long total = entryRepository.countByThreadId(threadId);
        int from = (int) Math.min((long) pageable.getOffset(), Math.max(0, all.size()));
        int to = (int) Math.min((long) pageable.getOffset() + pageable.getPageSize(), all.size());
        List<StoredEntry> content = all.subList(from, to).stream()
                .map(e -> new StoredEntry(e.getEntryId(), e.getAuthorUserId(), e.getContent(), e.getCreatedAt()))
                .toList();
        return new StoredPage<>(content, total);
    }

    private List<StoredThread> toStoredList(List<MongoClarificationThread> threads) {
        if (threads.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = threads.stream().map(MongoClarificationThread::getMessageId).distinct().toList();
        Map<Long, MongoMessage> messages = messageRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.toMap(MongoMessage::getMessageId, m -> m, (a, b) -> a));
        return threads.stream()
                .map(t -> toStored(t, messages.get(t.getMessageId())))
                .toList();
    }

    private StoredThread toStored(MongoClarificationThread t) {
        return toStored(t, messageRepository.findByMessageId(t.getMessageId()).orElse(null));
    }

    private StoredThread toStored(MongoClarificationThread t, MongoMessage message) {
        return new StoredThread(
                t.getThreadId(),
                t.getMessageId(),
                message != null ? message.getTitle() : null,
                t.getRequesterUserId(),
                t.getSenderUserId(),
                message != null ? message.getSenderRole() : null,
                toStatus(t.getStatus()),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private ClarificationStatus toStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return ClarificationStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private StoredMessage toStored(MongoMessage m) {
        return new StoredMessage(
                m.getMessageId(),
                m.getSenderUserId(),
                m.getSenderRole(),
                m.getTitle(),
                m.getContent(),
                m.getMessageType(),
                m.getCreatedAt());
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}