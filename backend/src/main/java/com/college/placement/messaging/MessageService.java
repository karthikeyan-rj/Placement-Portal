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
import com.college.placement.messaging.dto.MessageResponse;
import com.college.placement.messaging.dto.MyReactionResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final MessageReactionRepository reactionRepository;
    private final ClarificationThreadRepository clarificationThreadRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_INSERT_THRESHOLD = 25;

    @Transactional
    public MessageResponse sendMessage(CreateMessageRequest request) {
        User sender = securityUtils.getCurrentUser();
        Role senderRole = sender.getRole();

        Message message = Message.builder()
                .sender(sender)
                .title(request.getTitle())
                .content(request.getContent())
                .messageType(resolveMessageType(request.getMessageType(), senderRole))
                .build();
        messageRepository.save(message);

        List<User> recipients = resolveRecipients(request, sender);
        if (recipients.isEmpty()) {
            throw new BadRequestException("No valid recipients found for this message.");
        }

        LocalDateTime deliveredAt = LocalDateTime.now();
        if (recipients.size() >= BATCH_INSERT_THRESHOLD) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO message_recipients (message_id, recipient_id, delivered_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    recipients,
                    500,
                    (ps, recipient) -> {
                        ps.setLong(1, message.getId());
                        ps.setLong(2, recipient.getId());
                        ps.setTimestamp(3, Timestamp.valueOf(deliveredAt));
                        ps.setTimestamp(4, Timestamp.valueOf(deliveredAt));
                        ps.setTimestamp(5, Timestamp.valueOf(deliveredAt));
                    });
        } else {
            List<MessageRecipient> entities = recipients.stream()
                    .map(r -> MessageRecipient.builder()
                            .message(message)
                            .recipient(r)
                            .deliveredAt(deliveredAt)
                            .build())
                    .toList();
            recipientRepository.saveAll(entities);
        }

        auditService.log("SEND_MESSAGE", "Message", message.getId(),
                "To " + recipients.size() + " recipients");

        return toResponse(message, loadStats(List.of(message.getId())), Map.of(), Map.of(), Map.of());
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getSentMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        Page<Message> page = messageRepository.findBySenderIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        return buildResponses(page, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getReceivedMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        Page<Message> page = messageRepository.findMessagesReceivedByUser(currentUser.getId(), pageable);
        return buildResponses(page, pageable, currentUser.getId());
    }

    @Transactional
    public void markAsRead(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        MessageRecipient recipient = recipientRepository.findByMessageIdAndRecipientId(messageId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Message recipient"));
        if (recipient.getReadAt() == null) {
            recipient.setReadAt(LocalDateTime.now());
            recipientRepository.save(recipient);
        }
    }

    @Transactional
    public void addReaction(Long messageId, MessageReactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        if (!recipientRepository.existsByMessageIdAndRecipientId(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only react to messages addressed to you.");
        }

        MessageReactionType reactionType;
        try {
            reactionType = MessageReactionType.valueOf(request.getReaction().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid reaction type: " + request.getReaction());
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        MessageReaction existingReaction = reactionRepository.findByMessageIdAndUserId(messageId, currentUser.getId()).orElse(null);

        if (existingReaction != null) {
            existingReaction.setReaction(reactionType);
            reactionRepository.save(existingReaction);
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .message(message)
                    .user(currentUser)
                    .reaction(reactionType)
                    .build();
            reactionRepository.save(reaction);
        }
    }

    @Transactional(readOnly = true)
    public MyReactionResponse getMyReaction(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        if (!recipientRepository.existsByMessageIdAndRecipientId(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only view reactions for messages addressed to you.");
        }
        MessageReaction reaction = reactionRepository.findByMessageIdAndUserId(messageId, currentUser.getId())
                .orElse(null);
        return new MyReactionResponse(reaction != null ? reaction.getReaction().name() : null);
    }

    @Transactional(readOnly = true)
    public long getMessageAnalytics(Long messageId, String analyticsType) {
        User currentUser = securityUtils.getCurrentUser();

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the sender can view message analytics.");
        }

        switch (analyticsType.toUpperCase()) {
            case "TOTAL": return recipientRepository.countByMessageId(messageId);
            case "DELIVERED": return recipientRepository.countByMessageIdAndDeliveredAtIsNotNull(messageId);
            case "READ": return recipientRepository.countByMessageIdAndReadAtIsNotNull(messageId);
            case "UPVOTE": return reactionRepository.countByMessageIdAndReaction(messageId, MessageReactionType.UPVOTE);
            case "DOWNVOTE": return reactionRepository.countByMessageIdAndReaction(messageId, MessageReactionType.DOWNVOTE);
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

    private Page<MessageResponse> buildResponses(Page<Message> page, Pageable pageable, Long recipientUserId) {
        List<Message> messages = page.getContent();
        List<Long> ids = messages.stream().map(Message::getId).toList();

        Map<Long, MessageStats> stats = loadStats(ids);
        Map<Long, ClarificationSummary> clarif = recipientUserId == null ? loadClarificationSummaries(ids) : Map.of();
        Map<Long, Boolean> readFlags = recipientUserId == null ? Map.of() : loadReadFlags(ids, recipientUserId);
        Map<Long, MessageReactionType> myReactions = recipientUserId == null ? Map.of() : loadMyReactions(ids, recipientUserId);

        List<MessageResponse> responses = messages.stream()
                .map(m -> toResponse(m, stats, clarif, readFlags, myReactions))
                .toList();
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    private Map<Long, Boolean> loadReadFlags(List<Long> ids, Long recipientUserId) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Boolean> flags = new HashMap<>();
        for (MessageRecipientRepository.MessageReadFlag f : recipientRepository.findReadFlags(ids, recipientUserId)) {
            flags.put(f.getMessageId(), Boolean.TRUE.equals(f.getReadFlag()));
        }
        return flags;
    }

    private Map<Long, MessageReactionType> loadMyReactions(List<Long> ids, Long recipientUserId) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, MessageReactionType> reactions = new HashMap<>();
        for (MessageReactionRepository.MyReactionFlag f : reactionRepository.findMyReactions(ids, recipientUserId)) {
            reactions.put(f.getMessageId(), f.getReaction());
        }
        return reactions;
    }

    private Map<Long, ClarificationSummary> loadClarificationSummaries(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ClarificationSummary> summaries = new HashMap<>();
        for (ClarificationThreadRepository.ClarificationSummaryStats s : clarificationThreadRepository.aggregateSummary(ids)) {
            long total = s.getTotal() != null ? s.getTotal() : 0L;
            long open = s.getOpen() != null ? s.getOpen() : 0L;
            summaries.put(s.getMessageId(), new ClarificationSummary(total, open, total - open));
        }
        return summaries;
    }

    private Map<Long, MessageStats> loadStats(List<Long> messageIds) {
        if (messageIds.isEmpty()) return Map.of();

        Map<Long, MessageStats> stats = new HashMap<>();
        for (MessageRecipientRepository.MessageRecipientStats s : recipientRepository.aggregateStats(messageIds)) {
            stats.put(s.getMessageId(), new MessageStats(
                    s.getTotal() != null ? s.getTotal() : 0L,
                    s.getDelivered() != null ? s.getDelivered() : 0L,
                    s.getRead() != null ? s.getRead() : 0L,
                    0L, 0L));
        }
        for (MessageReactionRepository.MessageReactionStats r : reactionRepository.aggregateStats(messageIds)) {
            MessageStats s = stats.computeIfAbsent(r.getMessageId(), id -> new MessageStats(0L, 0L, 0L, 0L, 0L));
            stats.put(r.getMessageId(), new MessageStats(
                    s.total(), s.delivered(), s.read(),
                    r.getUpvotes() != null ? r.getUpvotes() : 0L,
                    r.getDownvotes() != null ? r.getDownvotes() : 0L));
        }
        return stats;
    }

    private record MessageStats(long total, long delivered, long read, long upvotes, long downvotes) {}

    private record ClarificationSummary(long total, long open, long answered) {}

    private MessageResponse toResponse(Message message,
                                       Map<Long, MessageStats> stats,
                                       Map<Long, ClarificationSummary> clarif,
                                       Map<Long, Boolean> readFlags,
                                       Map<Long, MessageReactionType> myReactions) {
        MessageStats s = stats.getOrDefault(message.getId(), new MessageStats(0L, 0L, 0L, 0L, 0L));
        ClarificationSummary c = clarif.getOrDefault(message.getId(), new ClarificationSummary(0L, 0L, 0L));
        MessageReactionType my = myReactions.get(message.getId());
        return MessageResponse.builder()
                .id(message.getId())
                .senderName(message.getSender().getName())
                .senderRole(message.getSender().getRole().name())
                .title(message.getTitle())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt().toString())
                .totalRecipients((int) s.total)
                .deliveredCount((int) s.delivered)
                .readCount((int) s.read)
                .upvoteCount((int) s.upvotes)
                .downvoteCount((int) s.downvotes)
                .clarificationCount(c.total())
                .openClarificationCount(c.open())
                .answeredClarificationCount(c.answered())
                .readByRecipient(Boolean.TRUE.equals(readFlags.get(message.getId())))
                .myReaction(my != null ? my.name() : null)
                .build();
    }
}
