package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.MessageReactionType;
import com.college.placement.common.enums.MessageType;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.messaging.dto.CreateMessageRequest;
import com.college.placement.messaging.dto.MessageReactionRequest;
import com.college.placement.messaging.dto.MessageNotification;
import com.college.placement.messaging.dto.MessageResponse;
import com.college.placement.messaging.dto.MyReactionResponse;
import com.college.placement.messaging.dto.UnreadCountResponse;
import com.college.placement.messaging.store.MessagingStore;
import com.college.placement.messaging.store.MessagingStore.ClarificationSummaryRow;
import com.college.placement.messaging.store.MessagingStore.MessageStatsRow;
import com.college.placement.messaging.store.MessagingStore.ReactionRow;
import com.college.placement.messaging.store.MessagingStore.ReadFlagRow;
import com.college.placement.messaging.store.MessagingStore.StoredMessage;
import com.college.placement.messaging.store.MessagingStore.StoredPage;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessagingStore store;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final MessageNotificationService notificationService;

    public MessageResponse sendMessage(CreateMessageRequest request) {
        User sender = securityUtils.getCurrentUser();
        Role senderRole = sender.getRole();

        List<User> recipients = resolveRecipients(request, sender);
        if (recipients.isEmpty()) {
            throw new BadRequestException("No valid recipients found for this message.");
        }

        StoredMessage created = store.createMessage(sender, request.getTitle(), request.getContent(),
                resolveMessageType(request.getMessageType(), senderRole), recipients, LocalDateTime.now());

        auditService.log("SEND_MESSAGE", "Message", created.messageId(),
                "To " + recipients.size() + " recipients");

        notificationService.publishNewMessage(
                recipients.stream().map(User::getId).toList(),
                MessageNotification.builder()
                        .type("NEW_MESSAGE")
                        .messageId(created.messageId())
                        .senderName(sender.getName())
                        .title(created.title())
                        .createdAt(created.createdAt() != null ? created.createdAt().toString() : null)
                        .build());

        return toResponse(created, sender.getName(),
                loadStats(store.messageStats(List.of(created.messageId()))),
                Map.of(), Map.of(), Map.of());
    }

    public Page<MessageResponse> getSentMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        StoredPage<StoredMessage> page = store.sentBySender(currentUser.getId(), pageable);
        return buildResponses(page, pageable, null);
    }

    public Page<MessageResponse> getReceivedMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        StoredPage<StoredMessage> page = store.receivedByUser(currentUser.getId(), pageable);
        return buildResponses(page, pageable, currentUser.getId());
    }

    public void markAsRead(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        if (!store.isRecipient(messageId, currentUser.getId())) {
            throw new ResourceNotFoundException("Message recipient");
        }
        store.markRead(messageId, currentUser.getId());
    }

    public UnreadCountResponse getUnreadCount() {
        User currentUser = securityUtils.getCurrentUser();
        return new UnreadCountResponse(store.countUnread(currentUser.getId()));
    }

    public SseEmitter subscribeToEvents() {
        User currentUser = securityUtils.getCurrentUser();
        return notificationService.subscribe(currentUser.getId());
    }

    public void addReaction(Long messageId, MessageReactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        if (!store.isRecipient(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only react to messages addressed to you.");
        }

        MessageReactionType reactionType;
        try {
            reactionType = MessageReactionType.valueOf(request.getReaction().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid reaction type: " + request.getReaction());
        }

        if (store.getMessage(messageId) == null) {
            throw new ResourceNotFoundException("Message", messageId);
        }

        store.setReaction(messageId, currentUser.getId(), reactionType);
    }

    public MyReactionResponse getMyReaction(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        if (!store.isRecipient(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only view reactions for messages addressed to you.");
        }
        MessageReactionType reaction = store.getMyReaction(messageId, currentUser.getId());
        return new MyReactionResponse(reaction != null ? reaction.name() : null);
    }

    public long getMessageAnalytics(Long messageId, String analyticsType) {
        User currentUser = securityUtils.getCurrentUser();

        StoredMessage message = store.getMessage(messageId);
        if (message == null) {
            throw new ResourceNotFoundException("Message", messageId);
        }
        if (!message.senderUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the sender can view message analytics.");
        }

        switch (analyticsType.toUpperCase()) {
            case "TOTAL": return store.analyticsTotal(messageId);
            case "DELIVERED": return store.analyticsDelivered(messageId);
            case "READ": return store.analyticsRead(messageId);
            case "UPVOTE": return store.analyticsUpvotes(messageId);
            case "DOWNVOTE": return store.analyticsDownvotes(messageId);
            default: throw new BadRequestException("Invalid analytics type: " + analyticsType);
        }
    }

    private List<User> resolveRecipients(CreateMessageRequest request, User sender) {
        Role senderRole = sender.getRole();

        if (request.getRecipientIds() != null && !request.getRecipientIds().isEmpty()) {
            return userRepository.findAllById(request.getRecipientIds()).stream()
                    .filter(u -> u.getActive() && validateRecipientPermission(senderRole, sender, u))
                    .toList();
        }

        if (request.getDepartmentId() != null) {
            final List<Role> targetRoles;
            if (request.getTargetRole() != null) {
                try {
                    targetRoles = List.of(Role.valueOf(request.getTargetRole().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid target role: " + request.getTargetRole());
                }
            } else {
                targetRoles = List.of(Role.STUDENT, Role.PR, Role.PC);
            }
            return userRepository.findByDepartmentId(request.getDepartmentId()).stream()
                    .filter(User::getActive)
                    .filter(u -> targetRoles.contains(u.getRole()))
                    .filter(u -> validateRecipientPermission(senderRole, sender, u))
                    .toList();
        }

        if (request.getTargetRole() != null) {
            try {
                Role targetRole = Role.valueOf(request.getTargetRole().toUpperCase());
                return userRepository.findByRole(targetRole).stream()
                        .filter(User::getActive)
                        .filter(u -> validateRecipientPermission(senderRole, sender, u))
                        .toList();
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid target role: " + request.getTargetRole());
            }
        }

        return new ArrayList<>();
    }

    private boolean validateRecipientPermission(Role senderRole, User sender, User recipient) {
        return switch (senderRole) {
            case PO -> true;
            case PC -> {
                if (recipient.getRole() == Role.PO) yield false;
                if (sender.getDepartment() == null) yield false;
                if (recipient.getDepartment() == null) yield false;
                yield sender.getDepartment().getId().equals(recipient.getDepartment().getId());
            }
            case PR -> {
                if (recipient.getRole() == Role.PO || recipient.getRole() == Role.PC) yield false;
                if (sender.getDepartment() == null || recipient.getDepartment() == null) yield false;
                yield sender.getDepartment().getId().equals(recipient.getDepartment().getId());
            }
            case STUDENT -> {
                if (recipient.getRole() == Role.PO) yield false;
                yield true;
            }
        };
    }

    private MessageType resolveMessageType(String type, Role role) {
        if (type != null) {
            try { return MessageType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException e) {}
        }
        return MessageType.DIRECT;
    }

    private Page<MessageResponse> buildResponses(StoredPage<StoredMessage> page, Pageable pageable, Long recipientUserId) {
        List<StoredMessage> messages = page.content();
        List<Long> ids = messages.stream().map(StoredMessage::messageId).toList();
        List<Long> senderIds = messages.stream().map(StoredMessage::senderUserId).distinct().toList();

        Map<Long, MessageStats> stats = loadStats(store.messageStats(ids));
        Map<Long, ClarificationSummary> clarif = recipientUserId == null
                ? loadClarificationSummaries(store.clarificationSummaries(ids)) : Map.of();
        Map<Long, Boolean> readFlags = recipientUserId == null
                ? Map.of() : loadReadFlags(store.readFlags(ids, recipientUserId));
        Map<Long, MessageReactionType> myReactions = recipientUserId == null
                ? Map.of() : loadMyReactions(store.myReactions(ids, recipientUserId));
        Map<Long, String> senderNames = namesById(senderIds);

        List<MessageResponse> responses = messages.stream()
                .map(m -> toResponse(m, senderNames.get(m.senderUserId()), stats, clarif, readFlags, myReactions))
                .toList();
        return new PageImpl<>(responses, pageable, page.totalElements());
    }

    private Map<Long, String> namesById(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        for (User u : userRepository.findUsersByIds(userIds)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private Map<Long, Boolean> loadReadFlags(List<ReadFlagRow> rows) {
        Map<Long, Boolean> flags = new HashMap<>();
        for (ReadFlagRow f : rows) {
            flags.put(f.messageId(), f.read());
        }
        return flags;
    }

    private Map<Long, MessageReactionType> loadMyReactions(List<ReactionRow> rows) {
        Map<Long, MessageReactionType> reactions = new HashMap<>();
        for (ReactionRow f : rows) {
            reactions.put(f.messageId(), f.reaction());
        }
        return reactions;
    }

    private Map<Long, ClarificationSummary> loadClarificationSummaries(List<ClarificationSummaryRow> rows) {
        Map<Long, ClarificationSummary> summaries = new HashMap<>();
        for (ClarificationSummaryRow s : rows) {
            summaries.put(s.messageId(), new ClarificationSummary(s.total(), s.open(), s.total() - s.open()));
        }
        return summaries;
    }

    private Map<Long, MessageStats> loadStats(List<MessageStatsRow> rows) {
        Map<Long, MessageStats> stats = new HashMap<>();
        for (MessageStatsRow s : rows) {
            stats.put(s.messageId(), new MessageStats(s.total(), s.delivered(), s.read(), s.upvotes(), s.downvotes()));
        }
        return stats;
    }

    private record MessageStats(long total, long delivered, long read, long upvotes, long downvotes) {}

    private record ClarificationSummary(long total, long open, long answered) {}

    private MessageResponse toResponse(StoredMessage message,
                                       String senderName,
                                       Map<Long, MessageStats> stats,
                                       Map<Long, ClarificationSummary> clarif,
                                       Map<Long, Boolean> readFlags,
                                       Map<Long, MessageReactionType> myReactions) {
        MessageStats s = stats.getOrDefault(message.messageId(), new MessageStats(0L, 0L, 0L, 0L, 0L));
        ClarificationSummary c = clarif.getOrDefault(message.messageId(), new ClarificationSummary(0L, 0L, 0L));
        MessageReactionType my = myReactions.get(message.messageId());
        return MessageResponse.builder()
                .id(message.messageId())
                .senderName(senderName != null ? senderName : "")
                .senderRole(message.senderRole())
                .title(message.title())
                .content(message.content())
                .messageType(message.messageType())
                .createdAt(message.createdAt() != null ? message.createdAt().toString() : null)
                .totalRecipients((int) s.total)
                .deliveredCount((int) s.delivered)
                .readCount((int) s.read)
                .upvoteCount((int) s.upvotes)
                .downvoteCount((int) s.downvotes)
                .clarificationCount(c.total())
                .openClarificationCount(c.open())
                .answeredClarificationCount(c.answered())
                .readByRecipient(Boolean.TRUE.equals(readFlags.get(message.messageId())))
                .myReaction(my != null ? my.name() : null)
                .build();
    }
}