package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.messaging.dto.ClarificationCountsResponse;
import com.college.placement.messaging.dto.ClarificationEntryResponse;
import com.college.placement.messaging.dto.ClarificationEvent;
import com.college.placement.messaging.dto.ClarificationResponse;
import com.college.placement.messaging.store.MessagingStore;
import com.college.placement.messaging.store.MessagingStore.RecipientExportRow;
import com.college.placement.messaging.store.MessagingStore.StoredEntry;
import com.college.placement.messaging.store.MessagingStore.StoredMessage;
import com.college.placement.messaging.store.MessagingStore.StoredPage;
import com.college.placement.messaging.store.MessagingStore.StoredThread;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClarificationService {

    private final MessagingStore store;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final MessageNotificationService messageNotificationService;

    public ClarificationResponse createClarification(Long messageId, String content) {
        User principal = securityUtils.getCurrentUser();
        requireActive(principal);
        User currentUser = managedUser(principal);

        StoredMessage message = store.getMessage(messageId);
        if (message == null) {
            throw new ResourceNotFoundException("Message", messageId);
        }

        Role senderRole = roleOf(message.senderRole());
        if (senderRole != Role.PO && senderRole != Role.PC) {
            throw new ForbiddenException("Clarifications are only available on messages sent by the Placement Officer or a Placement Coordinator.");
        }

        if (!store.isRecipient(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only ask for clarification on messages addressed to you.");
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Clarification content cannot be blank.");
        }

        StoredThread thread = store.getClarificationThread(messageId, currentUser.getId());
        if (thread == null) {
            thread = store.createClarificationThread(messageId, currentUser.getId(), message.senderUserId());
        }
        store.saveClarificationEntry(thread.threadId(), currentUser.getId(), trimmed);
        StoredThread updated = store.updateClarificationThreadStatus(thread.threadId(), ClarificationStatus.OPEN);

        auditService.log("CLARIFICATION_CREATED", "ClarificationThread", thread.threadId(),
                "Message " + messageId + " requester " + currentUser.getId());

        broadcastClarificationEvent("CLARIFICATION_CREATED", messageId, thread.threadId(),
                message.senderUserId());

        return toDetailResponse(updated, store.entriesForThread(thread.threadId(), Pageable.ofSize(50)));
    }

    public ClarificationResponse replyToThread(Long threadId, String content) {
        User principal = securityUtils.getCurrentUser();
        requireActive(principal);
        User currentUser = managedUser(principal);

        StoredThread thread = store.getClarificationThread(threadId);
        if (thread == null) {
            throw new ResourceNotFoundException("ClarificationThread", threadId);
        }

        if (!isParticipant(thread, currentUser.getId())) {
            throw new ForbiddenException("Only the requester and the original message sender can reply to this clarification.");
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Reply content cannot be blank.");
        }

        store.saveClarificationEntry(thread.threadId(), currentUser.getId(), trimmed);
        boolean bySender = thread.senderUserId().equals(currentUser.getId());
        StoredThread updated = store.updateClarificationThreadStatus(thread.threadId(),
                bySender ? ClarificationStatus.ANSWERED : ClarificationStatus.OPEN);

        auditService.log("CLARIFICATION_REPLIED", "ClarificationThread", thread.threadId(),
                "Message " + thread.messageId() + " author " + currentUser.getId());

        // The original sender's reply is the official answer; a requester reply is
        // a follow-up that reopens the thread. Both change what every legitimate
        // viewer of the parent message sees, so both emit a revalidation signal.
        broadcastClarificationEvent(bySender ? "CLARIFICATION_REPLIED" : "CLARIFICATION_FOLLOWUP",
                thread.messageId(), thread.threadId(), thread.senderUserId());

        return toDetailResponse(updated, store.entriesForThread(thread.threadId(), Pageable.ofSize(50)));
    }

    public Page<ClarificationResponse> listThreadsForMessage(Long messageId, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        StoredMessage message = store.getMessage(messageId);
        if (message == null) {
            throw new ResourceNotFoundException("Message", messageId);
        }

        if (message.senderUserId().equals(currentUser.getId())) {
            StoredPage<StoredThread> page = store.threadsForMessage(messageId, pageable);
            Map<Long, String> names = namesById(userIdsOf(page.content()));
            List<ClarificationResponse> responses = page.content().stream()
                    .map(t -> toSummaryResponse(t, names))
                    .toList();
            return new PageImpl<>(responses, pageable, page.totalElements());
        }


        throw new ForbiddenException("You cannot view clarifications for this message.");
    }

    public ClarificationResponse getThread(Long threadId, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        StoredThread thread = store.getClarificationThread(threadId);
        if (thread == null) {
            throw new ResourceNotFoundException("ClarificationThread", threadId);
        }

        if (!isParticipant(thread, currentUser.getId())) {
            throw new ForbiddenException("You cannot view this clarification thread.");
        }

        return toDetailResponse(thread, store.entriesForThread(threadId, pageable));
    }

    public ClarificationCountsResponse getCounts(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        StoredMessage message = store.getMessage(messageId);
        if (message == null) {
            throw new ResourceNotFoundException("Message", messageId);
        }

        if (!message.senderUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the sender can view clarification analytics.");
        }

        long total = store.clarificationTotal(messageId);
        long open = store.clarificationOpen(messageId);
        return ClarificationCountsResponse.builder()
                .total(total)
                .open(open)
                .answered(total - open)
                .build();
    }

    public Page<ClarificationResponse> getIncoming(Pageable pageable) {
        securityUtils.requireAnyRole(Role.PO, Role.PC);
        User currentUser = securityUtils.getCurrentUser();
        StoredPage<StoredThread> page = store.incomingThreads(currentUser.getId(), pageable);
        Map<Long, String> names = namesById(userIdsOf(page.content()));
        List<ClarificationResponse> responses = page.content().stream()
                .map(t -> toSummaryResponse(t, names))
                .toList();
        return new PageImpl<>(responses, pageable, page.totalElements());
    }

    private boolean isParticipant(StoredThread thread, Long userId) {
        return thread.requesterUserId().equals(userId) || thread.senderUserId().equals(userId);
    }

    private User managedUser(User principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ForbiddenException("User not found"));
    }

    private void requireActive(User user) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ForbiddenException("Inactive users cannot use clarifications.");
        }
    }

    private Role roleOf(String role) {
        if (role == null) {
            return null;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Long> userIdsOf(List<StoredThread> threads) {
        return threads.stream().flatMap(t -> java.util.stream.Stream.of(t.requesterUserId(), t.senderUserId()))
                .distinct().toList();
    }

    private Map<Long, String> namesById(List<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User u : userRepository.findUsersByIds(userIds)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private ClarificationResponse toSummaryResponse(StoredThread thread, Map<Long, String> names) {
        return ClarificationResponse.builder()
                .threadId(thread.threadId())
                .messageId(thread.messageId())
                .messageTitle(thread.messageTitle())
                .requesterId(thread.requesterUserId())
                .requesterName(names.getOrDefault(thread.requesterUserId(), ""))
                .senderId(thread.senderUserId())
                .senderName(names.getOrDefault(thread.senderUserId(), ""))
                .senderRole(thread.senderRole())
                .status(thread.status())
                .createdAt(thread.createdAt() != null ? thread.createdAt().toString() : null)
                .updatedAt(thread.updatedAt() != null ? thread.updatedAt().toString() : null)
                .build();
    }

    /**
     * Pushes a lightweight revalidation signal to the original message sender and
     * to every actual persisted recipient of the parent message.
     *
     * <p>Recipients come exclusively from the persisted {@code MessageRecipient}
     * documents for the parent message; role, department and audience are never
     * recomputed, so the target set can never exceed the real audience. The ids
     * are resolved with a single query and de-duplicated, so a sender who is also
     * a recipient (or a user with several open tabs) receives exactly one event
     * per connected stream. Nothing is sent for offline users: the data already
     * lives in Mongo and is read authoritatively when they open the message.
     *
     * <p>Never called before the write has been persisted and audited, and never
     * throws into the caller's transaction.
     */
    private void broadcastClarificationEvent(String type, Long messageId, Long threadId, Long senderUserId) {
        Set<Long> targets = new LinkedHashSet<>();
        if (senderUserId != null) {
            targets.add(senderUserId);
        }
        try {
            for (RecipientExportRow row : store.recipientsForExport(messageId)) {
                if (row.userId() != null) {
                    targets.add(row.userId());
                }
            }
        } catch (RuntimeException ex) {
            // The clarification is already persisted; a failed revalidation lookup
            // must never turn a successful write into an error for the client.
            return;
        }
        if (targets.isEmpty()) {
            return;
        }
        ClarificationEvent payload = ClarificationEvent.builder()
                .type(type)
                .messageId(messageId)
                .threadId(threadId)
                .build();
        messageNotificationService.broadcast(List.copyOf(targets), type, payload);
    }

    private ClarificationResponse toDetailResponse(StoredThread thread, StoredPage<StoredEntry> entries) {
        List<Long> ids = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(thread.requesterUserId(), thread.senderUserId()),
                        entries.content().stream().map(StoredEntry::authorUserId))
                .distinct().toList();
        Map<Long, String> names = namesById(ids);
        List<ClarificationEntryResponse> entryResponses = entries.content().stream()
                .map(e -> ClarificationEntryResponse.builder()
                        .id(e.entryId())
                        .authorId(e.authorUserId())
                        .authorName(names.getOrDefault(e.authorUserId(), ""))
                        .content(e.content())
                        .createdAt(e.createdAt() != null ? e.createdAt().toString() : null)
                        .build())
                .toList();
        return ClarificationResponse.builder()
                .threadId(thread.threadId())
                .messageId(thread.messageId())
                .messageTitle(thread.messageTitle())
                .requesterId(thread.requesterUserId())
                .requesterName(names.getOrDefault(thread.requesterUserId(), ""))
                .senderId(thread.senderUserId())
                .senderName(names.getOrDefault(thread.senderUserId(), ""))
                .senderRole(thread.senderRole())
                .status(thread.status())
                .createdAt(thread.createdAt() != null ? thread.createdAt().toString() : null)
                .updatedAt(thread.updatedAt() != null ? thread.updatedAt().toString() : null)
                .entries(entryResponses)
                .totalEntries(entries.totalElements())
                .build();
    }
}
