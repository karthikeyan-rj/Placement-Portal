package com.college.placement.messaging.email;

import com.college.placement.messaging.mongo.document.MongoMockSentEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailClient implements EmailDispatchClient {

    private final EmailProperties props;
    private final MongoTemplate mongoTemplate;

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String send(EmailDraft draft) throws EmailSendException {
        switch (props.getMockFailure()) {
            case AUTH:
                throw new EmailSendException(EmailSendException.Category.AUTH, "MOCK_AUTH");
            case RATE_LIMIT:
                throw new EmailSendException(EmailSendException.Category.RATE_LIMIT, "MOCK_RATE_LIMIT");
            case SERVER_ERROR:
                throw new EmailSendException(EmailSendException.Category.SERVER, "MOCK_SERVER_ERROR");
            case TIMEOUT:
                throw new EmailSendException(EmailSendException.Category.TIMEOUT, "MOCK_TIMEOUT");
            default:
                break;
        }
        String providerMessageId = "mock-" + UUID.randomUUID();
        mongoTemplate.insert(MongoMockSentEmail.builder()
                .messageId(draft.messageId())
                .recipientUserId(draft.recipientUserId())
                .email(draft.toEmail())
                .subject(draft.subject())
                .providerMessageId(providerMessageId)
                .sentAt(LocalDateTime.now())
                .build());
        return providerMessageId;
    }
}