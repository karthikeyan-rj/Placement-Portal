package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageEmailOutbox;
import com.college.placement.messaging.mongo.repository.MessageEmailOutboxRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.result.UpdateResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Content contract for HIGH importance notification emails.
 *
 * <p>The subject line and the four mandatory body elements are part of what the placement
 * office agreed to send, so they are asserted literally rather than by loose substring.
 */
class EmailNotificationHighTemplateTest {

    private EmailProperties props;
    private MongoTemplate mongoTemplate;
    private MessageEmailOutboxRepository outboxRepository;
    private MongoMessageRepository messageRepository;
    private UserRepository userRepository;
    private EmailDispatchClient dispatchClient;
    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        props = new EmailProperties();
        props.setEnabled(true);
        props.setFrontendUrl("http://localhost:4173");
        mongoTemplate = mock(MongoTemplate.class);
        outboxRepository = mock(MessageEmailOutboxRepository.class);
        messageRepository = mock(MongoMessageRepository.class);
        userRepository = mock(UserRepository.class);
        dispatchClient = mock(EmailDispatchClient.class);
        service = new EmailNotificationService(props, mongoTemplate, outboxRepository,
                messageRepository, userRepository, dispatchClient);
        when(mongoTemplate.updateMulti(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
    }

    private MongoMessageEmailOutbox pendingJob() {
        return MongoMessageEmailOutbox.builder()
                .id("job-1")
                .messageId(77L)
                .recipientUserId(12L)
                .recipientEmail("karthikeyanrj@student.tce.edu")
                .status(EmailOutboxStatus.PENDING.name())
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .updatedAt(LocalDateTime.now().minusMinutes(2))
                .build();
    }

    private void givenMessage(String title, String content) {
        MongoMessage message = MongoMessage.builder()
                .id("m-77")
                .messageId(77L)
                .senderUserId(3L)
                .title(title)
                .content(content)
                .importance("HIGH")
                .messageType("BROADCAST")
                .createdAt(LocalDateTime.now())
                .build();
        when(messageRepository.findByMessageIdIn(List.of(77L))).thenReturn(List.of(message));
        when(userRepository.findUsersByIds(any(java.util.Collection.class)))
                .thenReturn(List.of(User.builder().id(3L).name("Dr. Anita Rao").build()));
    }

    @Test
    @DisplayName("the HIGH notification uses the required subject and carries sender, title, content and CTA")
    void highNotificationMatchesRequiredTemplate() {
        givenMessage("Semester 7 company registration opens", "Register on the portal before the cutoff.");
        when(mongoTemplate.find(any(org.springframework.data.mongodb.core.query.Query.class),
                any(Class.class))).thenReturn(List.of(pendingJob()));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class))).thenReturn("prov-123");

        service.processDueBatch();

        ArgumentCaptor<EmailDraft> captor = ArgumentCaptor.forClass(EmailDraft.class);
        org.mockito.Mockito.verify(dispatchClient).send(captor.capture());
        EmailDraft draft = captor.getValue();

        assertThat(draft.subject()).isEqualTo("[High Priority] Semester 7 company registration opens");
        assertThat(draft.toEmail()).isEqualTo("karthikeyanrj@student.tce.edu");
        assertThat(draft.html())
                .contains("Dr. Anita Rao")
                .contains("Semester 7 company registration opens")
                .contains("Register on the portal before the cutoff.")
                .contains("View Message");
        assertThat(draft.text())
                .contains("From: Dr. Anita Rao")
                .contains("Semester 7 company registration opens")
                .contains("Register on the portal before the cutoff.")
                .contains("View Message:");
    }

    @Test
    @DisplayName("a header injection attempt in the title is neutralised in the subject")
    void subjectCannotInjectHeaders() {
        givenMessage("Urgent\r\nBcc: victim@college.edu", "body");
        when(mongoTemplate.find(any(org.springframework.data.mongodb.core.query.Query.class),
                any(Class.class))).thenReturn(List.of(pendingJob()));
        when(dispatchClient.isConfigured()).thenReturn(true);
        when(dispatchClient.send(any(EmailDraft.class))).thenReturn("prov-124");

        service.processDueBatch();

        ArgumentCaptor<EmailDraft> captor = ArgumentCaptor.forClass(EmailDraft.class);
        org.mockito.Mockito.verify(dispatchClient).send(captor.capture());
        assertThat(captor.getValue().subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("an orphan outbox row with no message is failed, not sent")
    void orphanRowIsFailed() {
        when(mongoTemplate.find(any(org.springframework.data.mongodb.core.query.Query.class),
                any(Class.class))).thenReturn(List.of(pendingJob()));
        when(messageRepository.findByMessageIdIn(List.of(77L))).thenReturn(List.of());
        when(dispatchClient.isConfigured()).thenReturn(true);

        service.processDueBatch();

        org.mockito.Mockito.verify(dispatchClient, org.mockito.Mockito.never()).send(any(EmailDraft.class));
    }

    @Test
    @DisplayName("nothing is dispatched while notifications are disabled")
    void disabledShortCircuits() {
        props.setEnabled(false);
        when(mongoTemplate.find(any(org.springframework.data.mongodb.core.query.Query.class),
                any(Class.class))).thenReturn(List.of(pendingJob()));

        service.processDueBatch();

        org.mockito.Mockito.verify(dispatchClient, org.mockito.Mockito.never()).send(any(EmailDraft.class));
    }
}
