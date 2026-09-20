package com.college.placement.messaging;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.messaging.dto.ClarificationCountsResponse;
import com.college.placement.messaging.dto.ClarificationEntryResponse;
import com.college.placement.messaging.dto.ClarificationResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClarificationService {

    private final ClarificationThreadRepository threadRepository;
    private final ClarificationEntryRepository entryRepository;
    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional
    public ClarificationResponse createClarification(Long messageId, String content) {
        User principal = securityUtils.getCurrentUser();
        requireActive(principal);
        User currentUser = managedUser(principal);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        Role senderRole = message.getSender().getRole();
        if (senderRole != Role.PO && senderRole != Role.PC) {
            throw new ForbiddenException("Clarifications are only available on messages sent by the Placement Officer or a Placement Coordinator.");
        }

        if (!recipientRepository.existsByMessageIdAndRecipientId(messageId, currentUser.getId())) {
            throw new ForbiddenException("You can only ask for clarification on messages addressed to you.");
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Clarification content cannot be blank.");
        }

        ClarificationThread thread = threadRepository.findByMessageIdAndRequesterId(messageId, currentUser.getId())
                .orElseGet(() -> ClarificationThread.builder()
                        .message(message)
                        .requester(currentUser)
                        .sender(message.getSender())
                        .status(ClarificationStatus.OPEN)
                        .build());

        thread = threadRepository.save(thread);
        saveEntry(thread, currentUser, trimmed);
        thread.setStatus(ClarificationStatus.OPEN);
        thread = threadRepository.save(thread);

        auditService.log("CLARIFICATION_CREATED", "ClarificationThread", thread.getId(),
                "Message " + messageId + " requester " + currentUser.getId());

        return toDetailResponse(thread, loadEntries(thread.getId(), Pageable.ofSize(50)));
    }

    @Transactional
    public ClarificationResponse replyToThread(Long threadId, String content) {
        User principal = securityUtils.getCurrentUser();
        requireActive(principal);
        User currentUser = managedUser(principal);

        ClarificationThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("ClarificationThread", threadId));

        if (!isParticipant(thread, currentUser.getId())) {
            throw new ForbiddenException("Only the requester and the original message sender can reply to this clarification.");
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Reply content cannot be blank.");
        }

        saveEntry(thread, currentUser, trimmed);
        boolean bySender = thread.getSender().getId().equals(currentUser.getId());
        thread.setStatus(bySender ? ClarificationStatus.ANSWERED : ClarificationStatus.OPEN);
        thread = threadRepository.save(thread);

        auditService.log("CLARIFICATION_REPLIED", "ClarificationThread", thread.getId(),
                "Message " + thread.getMessage().getId() + " author " + currentUser.getId());

        return toDetailResponse(thread, loadEntries(thread.getId(), Pageable.ofSize(50)));
    }

    @Transactional(readOnly = true)
    public Page<ClarificationResponse> listThreadsForMessage(Long messageId, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (message.getSender().getId().equals(currentUser.getId())) {
            return threadRepository.findByMessageIdOrderByUpdatedAtDesc(messageId, pageable)
                    .map(this::toSummaryResponse);
        }

        if (recipientRepository.existsByMessageIdAndRecipientId(messageId, currentUser.getId())) {
            var thread = threadRepository.findByMessageIdAndRequesterId(messageId, currentUser.getId());
            if (thread.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            var single = List.of(toSummaryResponse(thread.get()));
            return new PageImpl<>(single, pageable, 1);
        }

        throw new ForbiddenException("You cannot view clarifications for this message.");
    }

    @Transactional(readOnly = true)
    public ClarificationResponse getThread(Long threadId, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        ClarificationThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("ClarificationThread", threadId));

        if (!isParticipant(thread, currentUser.getId())) {
            throw new ForbiddenException("You cannot view this clarification thread.");
        }

        return toDetailResponse(thread, loadEntries(threadId, pageable));
    }

    @Transactional(readOnly = true)
    public ClarificationCountsResponse getCounts(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the sender can view clarification analytics.");
        }

        long total = threadRepository.countByMessageId(messageId);
        long open = threadRepository.countByMessageIdAndStatus(messageId, ClarificationStatus.OPEN);
        return ClarificationCountsResponse.builder()
                .total(total)
                .open(open)
                .answered(total - open)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ClarificationResponse> getIncoming(Pageable pageable) {
        securityUtils.requireAnyRole(Role.PO, Role.PC);
        User currentUser = securityUtils.getCurrentUser();
        return threadRepository.findBySenderIdOrderByUpdatedAtDesc(currentUser.getId(), pageable)
                .map(this::toSummaryResponse);
    }

    private Page<ClarificationEntry> loadEntries(Long threadId, Pageable pageable) {
        return entryRepository.findByThreadIdOrderByCreatedAtAsc(threadId, pageable);
    }

    private void saveEntry(ClarificationThread thread, User author, String content) {
        ClarificationEntry entry = ClarificationEntry.builder()
                .thread(thread)
                .author(author)
                .content(content)
                .build();
        entryRepository.save(entry);
    }

    private boolean isParticipant(ClarificationThread thread, Long userId) {
        return thread.getRequester().getId().equals(userId) || thread.getSender().getId().equals(userId);
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

    private ClarificationResponse toSummaryResponse(ClarificationThread thread) {
        return ClarificationResponse.builder()
                .threadId(thread.getId())
                .messageId(thread.getMessage().getId())
                .messageTitle(thread.getMessage().getTitle())
                .requesterId(thread.getRequester().getId())
                .requesterName(thread.getRequester().getName())
                .senderId(thread.getSender().getId())
                .senderName(thread.getSender().getName())
                .senderRole(thread.getSender().getRole().name())
                .status(thread.getStatus())
                .createdAt(thread.getCreatedAt() != null ? thread.getCreatedAt().toString() : null)
                .updatedAt(thread.getUpdatedAt() != null ? thread.getUpdatedAt().toString() : null)
                .build();
    }

    private ClarificationResponse toDetailResponse(ClarificationThread thread, Page<ClarificationEntry> entries) {
        List<ClarificationEntryResponse> entryResponses = entries.getContent().stream()
                .map(e -> ClarificationEntryResponse.builder()
                        .id(e.getId())
                        .authorId(e.getAuthor().getId())
                        .authorName(e.getAuthor().getName())
                        .content(e.getContent())
                        .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                        .build())
                .toList();
        return ClarificationResponse.builder()
                .threadId(thread.getId())
                .messageId(thread.getMessage().getId())
                .messageTitle(thread.getMessage().getTitle())
                .requesterId(thread.getRequester().getId())
                .requesterName(thread.getRequester().getName())
                .senderId(thread.getSender().getId())
                .senderName(thread.getSender().getName())
                .senderRole(thread.getSender().getRole().name())
                .status(thread.getStatus())
                .createdAt(thread.getCreatedAt() != null ? thread.getCreatedAt().toString() : null)
                .updatedAt(thread.getUpdatedAt() != null ? thread.getUpdatedAt().toString() : null)
                .entries(entryResponses)
                .totalEntries(entries.getTotalElements())
                .build();
    }
}