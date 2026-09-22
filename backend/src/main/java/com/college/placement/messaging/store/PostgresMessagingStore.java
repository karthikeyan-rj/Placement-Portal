package com.college.placement.messaging.store;

import com.college.placement.common.enums.MessageReactionType;
import com.college.placement.common.enums.MessageType;
import com.college.placement.messaging.ClarificationEntry;
import com.college.placement.messaging.ClarificationEntryRepository;
import com.college.placement.messaging.ClarificationStatus;
import com.college.placement.messaging.ClarificationThread;
import com.college.placement.messaging.ClarificationThreadRepository;
import com.college.placement.messaging.Message;
import com.college.placement.messaging.MessageReaction;
import com.college.placement.messaging.MessageReactionRepository;
import com.college.placement.messaging.MessageRecipient;
import com.college.placement.messaging.MessageRecipientRepository;
import com.college.placement.messaging.MessageRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.storage", havingValue = "postgres", matchIfMissing = true)
public class PostgresMessagingStore implements MessagingStore {

    private static final int BATCH_INSERT_THRESHOLD = 25;

    @PostConstruct
    void logStorageMode() {
        log.info("[MESSAGING] activeStorage=postgres");
    }

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final MessageReactionRepository reactionRepository;
    private final ClarificationThreadRepository threadRepository;
    private final ClarificationEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public StoredMessage createMessage(User sender, String title, String content, MessageType type,
                                       List<User> recipients, LocalDateTime deliveredAt) {
        Message message = messageRepository.save(Message.builder()
                .sender(sender)
                .title(title)
                .content(content)
                .messageType(type)
                .build());
        final Message savedMessage = message;

        LocalDateTime delivered = deliveredAt != null ? deliveredAt : LocalDateTime.now();
        if (recipients.size() >= BATCH_INSERT_THRESHOLD) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO message_recipients (message_id, recipient_id, delivered_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    recipients,
                    500,
                    (ps, recipient) -> {
                        ps.setLong(1, savedMessage.getId());
                        ps.setLong(2, recipient.getId());
                        ps.setTimestamp(3, Timestamp.valueOf(delivered));
                        ps.setTimestamp(4, Timestamp.valueOf(delivered));
                        ps.setTimestamp(5, Timestamp.valueOf(delivered));
                    });
        } else {
            List<MessageRecipient> entities = recipients.stream()
                    .map(r -> MessageRecipient.builder()
                            .message(savedMessage)
                            .recipient(r)
                            .deliveredAt(delivered)
                            .build())
                    .toList();
            recipientRepository.saveAll(entities);
        }

        return toStored(savedMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredMessage getMessage(Long messageId) {
        return messageRepository.findById(messageId).map(this::toStored).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredPage<StoredMessage> sentBySender(Long senderUserId, Pageable pageable) {
        Page<Message> page = messageRepository.findBySenderIdOrderByCreatedAtDesc(senderUserId, pageable);
        return new StoredPage<>(page.getContent().stream().map(this::toStored).toList(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public StoredPage<StoredMessage> receivedByUser(Long userId, Pageable pageable) {
        Page<Message> page = messageRepository.findMessagesReceivedByUser(userId, pageable);
        return new StoredPage<>(page.getContent().stream().map(this::toStored).toList(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRecipient(Long messageId, Long userId) {
        return recipientRepository.existsByMessageIdAndRecipientId(messageId, userId);
    }

    @Override
    @Transactional
    public void markRead(Long messageId, Long userId) {
        recipientRepository.findByMessageIdAndRecipientId(messageId, userId)
                .filter(r -> r.getReadAt() == null)
                .ifPresent(r -> {
                    r.setReadAt(LocalDateTime.now());
                    recipientRepository.save(r);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long recipientUserId) {
        return recipientRepository.countByRecipientIdAndReadAtIsNull(recipientUserId);
    }

    @Override
    @Transactional
    public void setReaction(Long messageId, Long userId, MessageReactionType reaction) {
        MessageReaction existing = reactionRepository.findByMessageIdAndUserId(messageId, userId).orElse(null);
        if (existing != null) {
            existing.setReaction(reaction);
            reactionRepository.save(existing);
        } else {
            Message message = messageRepository.findById(messageId).orElseThrow();
            reactionRepository.save(MessageReaction.builder()
                    .message(message)
                    .user(userRepository.findById(userId).orElseThrow())
                    .reaction(reaction)
                    .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MessageReactionType getMyReaction(Long messageId, Long userId) {
        return reactionRepository.findByMessageIdAndUserId(messageId, userId)
                .map(MessageReaction::getReaction)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public long analyticsTotal(Long messageId) {
        return recipientRepository.countByMessageId(messageId);
    }

    @Override
    @Transactional(readOnly = true)
    public long analyticsDelivered(Long messageId) {
        return recipientRepository.countByMessageIdAndDeliveredAtIsNotNull(messageId);
    }

    @Override
    @Transactional(readOnly = true)
    public long analyticsRead(Long messageId) {
        return recipientRepository.countByMessageIdAndReadAtIsNotNull(messageId);
    }

    @Override
    @Transactional(readOnly = true)
    public long analyticsUpvotes(Long messageId) {
        return reactionRepository.countByMessageIdAndReaction(messageId, MessageReactionType.UPVOTE);
    }

    @Override
    @Transactional(readOnly = true)
    public long analyticsDownvotes(Long messageId) {
        return reactionRepository.countByMessageIdAndReaction(messageId, MessageReactionType.DOWNVOTE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageStatsRow> messageStats(Collection<Long> messageIds) {
        List<MessageStatsRow> rows = new ArrayList<>();
        Map<Long, MessageStatsRow> byId = new HashMap<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        for (MessageRecipientRepository.MessageRecipientStats s : recipientRepository.aggregateStats(messageIds)) {
            MessageStatsRow row = new MessageStatsRow(
                    s.getMessageId(),
                    s.getTotal() != null ? s.getTotal() : 0L,
                    s.getDelivered() != null ? s.getDelivered() : 0L,
                    s.getRead() != null ? s.getRead() : 0L,
                    0L,
                    0L);
            byId.put(row.messageId(), row);
        }
        for (MessageReactionRepository.MessageReactionStats r : reactionRepository.aggregateStats(messageIds)) {
            MessageStatsRow existing = byId.get(r.getMessageId());
            if (existing != null) {
                byId.put(r.getMessageId(), new MessageStatsRow(
                        existing.messageId(), existing.total(), existing.delivered(), existing.read(),
                        r.getUpvotes() != null ? r.getUpvotes() : 0L,
                        r.getDownvotes() != null ? r.getDownvotes() : 0L));
            } else {
                byId.put(r.getMessageId(), new MessageStatsRow(
                        r.getMessageId(), 0L, 0L, 0L,
                        r.getUpvotes() != null ? r.getUpvotes() : 0L,
                        r.getDownvotes() != null ? r.getDownvotes() : 0L));
            }
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadFlagRow> readFlags(Collection<Long> messageIds, Long userId) {
        List<ReadFlagRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        for (MessageRecipientRepository.MessageReadFlag f : recipientRepository.findReadFlags(messageIds, userId)) {
            rows.add(new ReadFlagRow(f.getMessageId(), Boolean.TRUE.equals(f.getReadFlag())));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionRow> myReactions(Collection<Long> messageIds, Long userId) {
        List<ReactionRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        for (MessageReactionRepository.MyReactionFlag f : reactionRepository.findMyReactions(messageIds, userId)) {
            rows.add(new ReactionRow(f.getMessageId(), f.getReaction()));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipientExportRow> recipientsForExport(Long messageId) {
        List<MessageRecipient> recipients = recipientRepository.findByMessageIdWithUserAndDepartment(messageId);
        Map<Long, String> reactionByUser = new HashMap<>();
        for (MessageReaction r : reactionRepository.findByMessageId(messageId)) {
            reactionByUser.put(r.getUser().getId(), r.getReaction().name());
        }
        return recipients.stream().map(r -> new RecipientExportRow(
                r.getRecipient().getId(),
                r.getRecipient().getName(),
                r.getRecipient().getDepartment() != null ? r.getRecipient().getDepartment().getId() : null,
                r.getRecipient().getDepartment() != null ? r.getRecipient().getDepartment().getName() : null,
                r.getDeliveredAt() != null,
                r.getReadAt() != null,
                reactionByUser.getOrDefault(r.getRecipient().getId(), "NONE"),
                r.getCreatedAt())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StoredThread getClarificationThread(Long threadId) {
        return threadRepository.findById(threadId).map(this::toStored).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredThread getClarificationThread(Long messageId, Long requesterUserId) {
        return threadRepository.findByMessageIdAndRequesterId(messageId, requesterUserId).map(this::toStored).orElse(null);
    }

    @Override
    @Transactional
    public StoredThread createClarificationThread(Long messageId, Long requesterUserId, Long senderUserId) {
        Message message = messageRepository.findById(messageId).orElseThrow();
        User requester = userRepository.findById(requesterUserId).orElseThrow();
        User sender = userRepository.findById(senderUserId).orElseThrow();
        ClarificationThread thread = ClarificationThread.builder()
                .message(message)
                .requester(requester)
                .sender(sender)
                .status(ClarificationStatus.OPEN)
                .build();
        return toStored(threadRepository.save(thread));
    }

    @Override
    @Transactional
    public StoredThread updateClarificationThreadStatus(Long threadId, ClarificationStatus status) {
        ClarificationThread thread = threadRepository.findById(threadId).orElseThrow();
        thread.setStatus(status);
        return toStored(threadRepository.save(thread));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredPage<StoredThread> threadsForMessage(Long messageId, Pageable pageable) {
        Page<ClarificationThread> page = threadRepository.findByMessageIdOrderByUpdatedAtDesc(messageId, pageable);
        return toThreadPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredPage<StoredThread> incomingThreads(Long senderUserId, Pageable pageable) {
        Page<ClarificationThread> page = threadRepository.findBySenderIdOrderByUpdatedAtDesc(senderUserId, pageable);
        return toThreadPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public long clarificationTotal(Long messageId) {
        return threadRepository.countByMessageId(messageId);
    }

    @Override
    @Transactional(readOnly = true)
    public long clarificationOpen(Long messageId) {
        return threadRepository.countByMessageIdAndStatus(messageId, ClarificationStatus.OPEN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClarificationSummaryRow> clarificationSummaries(Collection<Long> messageIds) {
        List<ClarificationSummaryRow> rows = new ArrayList<>();
        if (messageIds.isEmpty()) {
            return rows;
        }
        for (ClarificationThreadRepository.ClarificationSummaryStats s : threadRepository.aggregateSummary(messageIds)) {
            long total = s.getTotal() != null ? s.getTotal() : 0L;
            long open = s.getOpen() != null ? s.getOpen() : 0L;
            rows.add(new ClarificationSummaryRow(s.getMessageId(), total, open));
        }
        return rows;
    }

    @Override
    @Transactional
    public StoredEntry saveClarificationEntry(Long threadId, Long authorUserId, String content) {
        ClarificationThread thread = threadRepository.findById(threadId).orElseThrow();
        User author = userRepository.findById(authorUserId).orElseThrow();
        ClarificationEntry entry = ClarificationEntry.builder()
                .thread(thread)
                .author(author)
                .content(content)
                .build();
        entry = entryRepository.save(entry);
        return new StoredEntry(entry.getId(), entry.getAuthor().getId(), entry.getContent(), entry.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public StoredPage<StoredEntry> entriesForThread(Long threadId, Pageable pageable) {
        Page<ClarificationEntry> page = entryRepository.findByThreadIdOrderByCreatedAtAsc(threadId, pageable);
        List<StoredEntry> content = page.getContent().stream()
                .map(e -> new StoredEntry(e.getId(), e.getAuthor().getId(), e.getContent(), e.getCreatedAt()))
                .toList();
        return new StoredPage<>(content, page.getTotalElements());
    }

    private StoredPage<StoredThread> toThreadPage(Page<ClarificationThread> page) {
        return new StoredPage<>(page.getContent().stream().map(this::toStored).toList(), page.getTotalElements());
    }

    private StoredThread toStored(ClarificationThread t) {
        return new StoredThread(
                t.getId(),
                t.getMessage().getId(),
                t.getMessage().getTitle(),
                t.getRequester().getId(),
                t.getSender().getId(),
                t.getSender().getRole().name(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private StoredMessage toStored(Message m) {
        return new StoredMessage(
                m.getId(),
                m.getSender().getId(),
                m.getSender().getRole().name(),
                m.getTitle(),
                m.getContent(),
                m.getMessageType().name(),
                m.getCreatedAt());
    }
}