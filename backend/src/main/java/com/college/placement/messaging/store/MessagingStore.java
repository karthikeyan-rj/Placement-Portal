package com.college.placement.messaging.store;

import com.college.placement.common.enums.MessageReactionType;
import com.college.placement.common.enums.MessageType;
import com.college.placement.messaging.ClarificationStatus;
import com.college.placement.user.User;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MessagingStore {

    StoredMessage createMessage(User sender, String title, String content, MessageType type,
                                List<User> recipients, LocalDateTime deliveredAt);

    StoredMessage getMessage(Long messageId);

    StoredPage<StoredMessage> sentBySender(Long senderUserId, Pageable pageable);

    StoredPage<StoredMessage> receivedByUser(Long userId, Pageable pageable);

    boolean isRecipient(Long messageId, Long userId);

    void markRead(Long messageId, Long userId);

    long countUnread(Long recipientUserId);

    void setReaction(Long messageId, Long userId, MessageReactionType reaction);

    MessageReactionType getMyReaction(Long messageId, Long userId);

    long analyticsTotal(Long messageId);

    long analyticsDelivered(Long messageId);

    long analyticsRead(Long messageId);

    long analyticsUpvotes(Long messageId);

    long analyticsDownvotes(Long messageId);

    List<MessageStatsRow> messageStats(Collection<Long> messageIds);

    List<ReadFlagRow> readFlags(Collection<Long> messageIds, Long userId);

    List<ReactionRow> myReactions(Collection<Long> messageIds, Long userId);

    List<RecipientExportRow> recipientsForExport(Long messageId);

    StoredThread getClarificationThread(Long threadId);

    StoredThread getClarificationThread(Long messageId, Long requesterUserId);

    StoredThread createClarificationThread(Long messageId, Long requesterUserId, Long senderUserId);

    StoredThread updateClarificationThreadStatus(Long threadId, ClarificationStatus status);

    StoredPage<StoredThread> threadsForMessage(Long messageId, Pageable pageable);

    StoredPage<StoredThread> incomingThreads(Long senderUserId, Pageable pageable);

    long clarificationTotal(Long messageId);

    long clarificationOpen(Long messageId);

    List<ClarificationSummaryRow> clarificationSummaries(Collection<Long> messageIds);

    StoredEntry saveClarificationEntry(Long threadId, Long authorUserId, String content);

    StoredPage<StoredEntry> entriesForThread(Long threadId, Pageable pageable);

    record StoredMessage(Long messageId, Long senderUserId, String senderRole, String title,
                         String content, String messageType, LocalDateTime createdAt) {
    }

    record MessageStatsRow(Long messageId, long total, long delivered, long read,
                           long upvotes, long downvotes) {
    }

    record ReadFlagRow(Long messageId, boolean read) {
    }

    record ReactionRow(Long messageId, MessageReactionType reaction) {
    }

    record RecipientExportRow(Long userId, String userName, Long departmentId, String departmentName,
                              boolean delivered, boolean read, String reaction, LocalDateTime createdAt) {
    }

    record StoredThread(Long threadId, Long messageId, String messageTitle, Long requesterUserId,
                        Long senderUserId, String senderRole, ClarificationStatus status,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    record ClarificationSummaryRow(Long messageId, long total, long open) {
    }

    record StoredEntry(Long entryId, Long authorUserId, String content, LocalDateTime createdAt) {
    }

    record StoredPage<T>(List<T> content, long totalElements) {
    }
}