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
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final MessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

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
        message = messageRepository.save(message);

        List<User> recipients = resolveRecipients(request, sender);
        if (recipients.isEmpty()) {
            throw new BadRequestException("No valid recipients found for this message.");
        }

        for (User recipient : recipients) {
            MessageRecipient msgRecipient = MessageRecipient.builder()
                    .message(message)
                    .recipient(recipient)
                    .deliveredAt(LocalDateTime.now())
                    .build();
            recipientRepository.save(msgRecipient);
        }

        auditService.log("SEND_MESSAGE", "Message", message.getId(),
                "To " + recipients.size() + " recipients");

        return toResponse(message);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getSentMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return messageRepository.findBySenderIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getReceivedMessages(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return messageRepository.findMessagesReceivedByUser(currentUser.getId(), pageable)
                .map(this::toResponse);
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
    public long getMessageAnalytics(Long messageId, String analyticsType) {
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

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .senderName(message.getSender().getName())
                .senderRole(message.getSender().getRole().name())
                .title(message.getTitle())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt().toString())
                .totalRecipients((int) recipientRepository.countByMessageId(message.getId()))
                .deliveredCount((int) recipientRepository.countByMessageIdAndDeliveredAtIsNotNull(message.getId()))
                .readCount((int) recipientRepository.countByMessageIdAndReadAtIsNotNull(message.getId()))
                .upvoteCount((int) reactionRepository.countByMessageIdAndReaction(message.getId(), MessageReactionType.UPVOTE))
                .downvoteCount((int) reactionRepository.countByMessageIdAndReaction(message.getId(), MessageReactionType.DOWNVOTE))
                .build();
    }
}
