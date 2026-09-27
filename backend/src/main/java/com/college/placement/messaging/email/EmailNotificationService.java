package com.college.placement.messaging.email;

import com.college.placement.messaging.dto.EmailStatusResponse;
import com.college.placement.messaging.mongo.document.MongoMessage;
import com.college.placement.messaging.mongo.document.MongoMessageEmailOutbox;
import com.college.placement.messaging.mongo.repository.MessageEmailOutboxRepository;
import com.college.placement.messaging.mongo.repository.MongoMessageRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\u0000-\\u001F]");

    private final EmailProperties props;
    private final MongoTemplate mongoTemplate;
    private final MessageEmailOutboxRepository outboxRepository;
    private final MongoMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EmailDispatchClient dispatchClient;

    public void enqueueHighPriority(Long messageId, List<User> recipients) {
        if (!props.isEnabled()) {
            log.debug("[EMAIL] notifications disabled; skipping outbox for message {}", messageId);
            return;
        }
        Set<Long> existingKeys = outboxRepository.findByMessageId(messageId).stream()
                .map(MongoMessageEmailOutbox::getRecipientUserId)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<MongoMessageEmailOutbox> toInsert = new ArrayList<>();
        for (User u : recipients) {
            if (existingKeys.contains(u.getId())) {
                continue;
            }
            String email = u.getEmail() == null ? "" : u.getEmail().trim();
            boolean valid = isValidEmail(email);
            toInsert.add(MongoMessageEmailOutbox.builder()
                    .messageId(messageId)
                    .recipientUserId(u.getId())
                    .recipientEmail(valid ? email : null)
                    .status(valid ? EmailOutboxStatus.PENDING.name() : EmailOutboxStatus.SKIPPED_INVALID_EMAIL.name())
                    .attempts(0)
                    .nextAttemptAt(valid ? now : null)
                    .lastError(valid ? null : "email missing or invalid")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        if (toInsert.isEmpty()) {
            return;
        }
        try {
            mongoTemplate.insert(toInsert, MongoMessageEmailOutbox.class);
        } catch (DuplicateKeyException e) {
            for (MongoMessageEmailOutbox doc : toInsert) {
                try {
                    mongoTemplate.insert(doc);
                } catch (DuplicateKeyException ignored) {
                    // row already exists for this (messageId, recipientUserId)
                }
            }
        }
    }

    public void processDueBatch() {
        if (!props.isEnabled()) {
            return;
        }
        requeueStaleInflight();
        List<MongoMessageEmailOutbox> claimed = claimBatch();
        if (claimed.isEmpty()) {
            return;
        }

        if (!dispatchClient.isConfigured()) {
            for (MongoMessageEmailOutbox job : claimed) {
                markStatus(job, EmailOutboxStatus.CONFIG_ERROR, null, "email provider not configured", null, null);
            }
            log.info("[EMAIL] Processed email notification batch: sent=0 failed={} (provider not configured)", claimed.size());
            return;
        }

        Map<Long, MongoMessage> messages = loadMessages(claimed);
        Map<Long, String> senderNames = loadSenderNames(messages);

        int sent = 0;
        int failed = 0;
        int retried = 0;
        for (MongoMessageEmailOutbox job : claimed) {
            MongoMessage message = messages.get(job.getMessageId());
            if (message == null) {
                markFailed(job, "message not found");
                failed++;
                continue;
            }
            EmailDraft draft = buildDraft(message, job, senderNames.get(message.getSenderUserId()));
            try {
                String providerMessageId = dispatchClient.send(draft);
                markSent(job, providerMessageId);
                sent++;
            } catch (EmailSendException ex) {
                int usedAttempts = job.getAttempts() == null ? 1 : job.getAttempts();
                if (ex.isPermanent() || usedAttempts >= props.getMaxAttempts()) {
                    markFailed(job, sanitize(ex.getCategory().name() + ": " + ex.getMessage()));
                    failed++;
                } else {
                    markForRetry(job);
                    retried++;
                }
            } catch (RuntimeException ex) {
                // A client-side fault must not abort the remaining batch or strand jobs as IN_FLIGHT.
                // The failure is persisted on the outbox row rather than swallowed.
                markFailed(job, sanitize("UNEXPECTED: " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage()));
                failed++;
            }
        }
        log.info("[EMAIL] Processed email notification batch: sent={} failed={} retried={}",
                sent, failed, retried);
    }

    public EmailStatusResponse statusSummary(Long messageId) {
        long pending = 0;
        long submitted = 0;
        long delivered = 0;
        long delayed = 0;
        long bounced = 0;
        long complained = 0;
        long suppressed = 0;
        long failed = 0;
        long skipped = 0;
        long configError = 0;
        long total = 0;
        List<Document> pipeline = List.of(
                new Document("$match", new Document("messageId", messageId)),
                new Document("$group", new Document("_id", "$status").append("n", new Document("$sum", 1))));
        for (Document doc : mongoTemplate.getCollection("message_email_outbox").aggregate(pipeline)) {
            long n = toLong(doc.get("n"));
            String status = doc.getString("_id");
            total += n;
            if (status == null) {
                pending += n;
                continue;
            }
            switch (status) {
                case "SUBMITTED", "SENT" -> submitted += n;
                case "DELIVERED" -> delivered += n;
                case "DELAYED" -> delayed += n;
                case "BOUNCED" -> bounced += n;
                case "COMPLAINED" -> complained += n;
                case "SUPPRESSED" -> suppressed += n;
                case "FAILED" -> failed += n;
                case "SKIPPED_INVALID_EMAIL" -> skipped += n;
                case "CONFIG_ERROR" -> configError += n;
                default -> pending += n;
            }
        }
        return EmailStatusResponse.builder()
                .pending(pending)
                .submitted(submitted)
                .delivered(delivered)
                .delayed(delayed)
                .bounced(bounced)
                .complained(complained)
                .suppressed(suppressed)
                .failed(failed)
                .skippedInvalid(skipped)
                .configError(configError)
                .total(total)
                .build();
    }

    private List<MongoMessageEmailOutbox> claimBatch() {
        Query query = new Query(Criteria.where("status").is(EmailOutboxStatus.PENDING.name())
                .and("nextAttemptAt").lte(LocalDateTime.now()));
        query.with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));
        query.limit(props.getBatchSize());
        List<MongoMessageEmailOutbox> candidates =
                mongoTemplate.find(query, MongoMessageEmailOutbox.class);

        List<MongoMessageEmailOutbox> claimed = new ArrayList<>();
        for (MongoMessageEmailOutbox candidate : candidates) {
            if (candidate.getAttempts() != null && candidate.getAttempts() >= props.getMaxAttempts()) {
                markStatus(candidate, EmailOutboxStatus.FAILED, null, "max attempts reached", null, null);
                continue;
            }
            Query claimQuery = new Query(Criteria.where("id").is(candidate.getId())
                    .and("status").is(EmailOutboxStatus.PENDING.name()));
            int nextAttempt = (candidate.getAttempts() == null ? 0 : candidate.getAttempts()) + 1;
            LocalDateTime now = LocalDateTime.now();
            UpdateResult result = mongoTemplate.updateFirst(claimQuery, new Update()
                            .set("status", EmailOutboxStatus.IN_FLIGHT.name())
                            .set("attempts", nextAttempt)
                            .set("startedAt", now)
                            .set("updatedAt", now),
                    MongoMessageEmailOutbox.class);
            if (result.getModifiedCount() == 1) {
                candidate.setStatus(EmailOutboxStatus.IN_FLIGHT.name());
                candidate.setAttempts(nextAttempt);
                candidate.setStartedAt(now);
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    private void requeueStaleInflight() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(props.getInflightTimeoutSeconds());
        long requeued = mongoTemplate.updateMulti(
                new Query(Criteria.where("status").is(EmailOutboxStatus.IN_FLIGHT.name())
                        .and("startedAt").lt(cutoff)),
                new Update().set("status", EmailOutboxStatus.PENDING.name())
                        .set("nextAttemptAt", LocalDateTime.now())
                        .set("startedAt", null)
                        .set("updatedAt", LocalDateTime.now()),
                MongoMessageEmailOutbox.class).getModifiedCount();
        if (requeued > 0) {
            log.info("[EMAIL] Requeued {} stale in-flight outbox jobs after restart", requeued);
        }
    }

    private Map<Long, MongoMessage> loadMessages(List<MongoMessageEmailOutbox> jobs) {
        Map<Long, MongoMessage> byId = new HashMap<>();
        List<Long> ids = jobs.stream().map(MongoMessageEmailOutbox::getMessageId)
                .distinct().toList();
        if (ids.isEmpty()) {
            return byId;
        }
        for (MongoMessage m : messageRepository.findByMessageIdIn(ids)) {
            byId.put(m.getMessageId(), m);
        }
        return byId;
    }

    private Map<Long, String> loadSenderNames(Map<Long, MongoMessage> messages) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> senderIds = messages.values().stream()
                .map(MongoMessage::getSenderUserId).collect(Collectors.toSet());
        if (senderIds.isEmpty()) {
            return names;
        }
        for (User u : userRepository.findUsersByIds(senderIds)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private EmailDraft buildDraft(MongoMessage message, MongoMessageEmailOutbox job, String senderName) {
        String subject = "[High Priority] " + sanitizeHeader(message.getTitle());
        String displayName = senderName != null && !senderName.isBlank() ? senderName : "Placement Officer";
        String link = props.getFrontendUrl().replaceAll("/+$", "") + "/messages?messageId=" + message.getMessageId();
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:auto\">"
                + "<h2 style=\"margin:0 0 4px\">High Priority Placement Communication</h2>"
                + "<p style=\"color:#555;margin:0 0 16px\">From: " + escapeHtml(displayName) + "</p>"
                + "<h3 style=\"margin:0 0 8px\">" + escapeHtml(message.getTitle()) + "</h3>"
                + "<div style=\"line-height:1.6\">" + toHtmlParagraphs(message.getContent()) + "</div>"
                + "<p style=\"margin-top:24px\"><a href=\"" + escapeHtmlAttr(link)
                + "\">View Message</a></p>"
                + "</div>";
        String text = "High Priority Placement Communication\n\n"
                + "From: " + displayName + "\n\n"
                + message.getTitle() + "\n\n"
                + message.getContent() + "\n\n"
                + "View Message:\n"
                + link + "\n";
        return new EmailDraft(message.getMessageId(), job.getRecipientUserId(),
                job.getRecipientEmail(), subject, html, text);
    }

    private void markSent(MongoMessageEmailOutbox job, String providerMessageId) {
        markStatus(job, EmailOutboxStatus.SUBMITTED, providerMessageId, null, LocalDateTime.now(), null);
    }

    /**
     * Applies a verified provider delivery event to the matching outbox row.
     * States are monotonic: a late non-terminal event cannot downgrade a terminal one.
     *
     * @return true when a row was updated
     */
    public boolean applyDeliveryEvent(String providerMessageId, EmailOutboxStatus status, String eventName) {
        if (providerMessageId == null || providerMessageId.isBlank() || status == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update()
                .set("status", status.name())
                .set("lastEventName", eventName)
                .set("lastEventAt", now)
                .set("updatedAt", now);
        if (status == EmailOutboxStatus.DELIVERED) {
            update.set("deliveredAt", now);
        }
        if (status == EmailOutboxStatus.BOUNCED || status == EmailOutboxStatus.COMPLAINED) {
            update.set("bouncedAt", now);
        }
        if (status == EmailOutboxStatus.SUPPRESSED) {
            update.set("suppressedAt", now);
        }
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("providerMessageId").is(providerMessageId)
                        .and("status").nin(EmailOutboxStatus.DELIVERED.name(),
                                EmailOutboxStatus.BOUNCED.name(),
                                EmailOutboxStatus.COMPLAINED.name(),
                                EmailOutboxStatus.SUPPRESSED.name())),
                update, MongoMessageEmailOutbox.class).getModifiedCount() > 0;
    }

    private void markFailed(MongoMessageEmailOutbox job, String lastError) {
        markStatus(job, EmailOutboxStatus.FAILED, null, sanitize(lastError), null, null);
    }

    private void markForRetry(MongoMessageEmailOutbox job) {
        LocalDateTime next = LocalDateTime.now()
                .plusSeconds(props.getRetryDelayBaseSeconds() * ((job.getAttempts() == null ? 0 : job.getAttempts())));
        Update update = new Update().set("status", EmailOutboxStatus.PENDING.name())
                .set("nextAttemptAt", next)
                .set("startedAt", null)
                .set("updatedAt", LocalDateTime.now());
        if (job.getLastError() == null) {
            update.set("lastError", "delivery failed, will retry");
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(job.getId())
                        .and("status").is(EmailOutboxStatus.IN_FLIGHT.name())),
                update, MongoMessageEmailOutbox.class);
    }

    private void markStatus(MongoMessageEmailOutbox job, EmailOutboxStatus status,
                            String providerMessageId, String lastError,
                            LocalDateTime sentAt, LocalDateTime nextAttemptAt) {
        Update update = new Update().set("status", status.name()).set("updatedAt", LocalDateTime.now());
        if (providerMessageId != null) {
            update.set("providerMessageId", providerMessageId);
        }
        if (lastError != null) {
            update.set("lastError", lastError);
        }
        if (sentAt != null) {
            update.set("sentAt", sentAt);
        }
        if (nextAttemptAt != null) {
            update.set("nextAttemptAt", nextAttemptAt);
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(job.getId())
                        .and("status").in(EmailOutboxStatus.PENDING.name(), EmailOutboxStatus.IN_FLIGHT.name())),
                update, MongoMessageEmailOutbox.class);
    }

    private boolean isValidEmail(String email) {
        return email != null && !email.isBlank()
                && !CONTROL_CHARS.matcher(email).find()
                && EMAIL_PATTERN.matcher(email).matches();
    }

    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ").trim();
        return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeHtmlAttr(String value) {
        return escapeHtml(value);
    }

    private String toHtmlParagraphs(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return String.join("<br/>",
                content.split("\\r?\\n"));
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}