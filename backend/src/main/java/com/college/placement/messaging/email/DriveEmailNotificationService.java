package com.college.placement.messaging.email;

import com.college.placement.messaging.dto.DriveEmailStatusResponse;
import com.college.placement.messaging.mongo.document.MongoDriveEmailOutbox;
import com.college.placement.messaging.mongo.repository.DriveEmailOutboxRepository;
import com.college.placement.placement.EligibilityCriteria;
import com.college.placement.placement.EligibilityCriteriaRepository;
import com.college.placement.placement.dto.DriveRecipientProjection;
import com.college.placement.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Placement Drive notification emails. Reuses the shared Resend client and the same
 * scheduled worker as Placement Messaging; only the outbox collection differs, because a
 * drive notification is not a portal message and must not appear in the Messages UI.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DriveEmailNotificationService {

    public static final String EVENT_REGISTRATION_OPEN = "REGISTRATION_OPEN";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\u0000-\\u001F]");
    private static final int INSERT_CHUNK = 500;

    private final EmailProperties props;
    private final MongoTemplate mongoTemplate;
    private final DriveEmailOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final EligibilityCriteriaRepository eligibilityRepository;
    private final EmailDispatchClient dispatchClient;

    /**
     * Resolves eligible recipients and persists one outbox row each. Never calls the
     * provider. Callers must invoke this after the drive status transition has committed.
     *
     * @return number of outbox rows queued
     */
    public int enqueueRegistrationOpen(Long driveId, String jobRole, String companyName,
                                       BigDecimal packageLpa, LocalDate driveDate, LocalDate registrationDeadline,
                                       String location) {
        if (!props.isEnabled()) {
            log.info("[EMAIL] Drive {} notification skipped: email notifications are disabled", driveId);
            return 0;
        }
        List<DriveRecipientProjection> recipients = resolveEligibleRecipients(driveId);
        if (recipients.isEmpty()) {
            log.info("[EMAIL] Drive {} has no eligible recipients to notify", driveId);
            return 0;
        }
        String batchId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String subject = "Placement Opportunity: " + trimTo(companyName, 80) + " \u2014 " + trimTo(jobRole, 80);
        String html = buildHtml(jobRole, companyName, packageLpa, driveDate, registrationDeadline, location);
        String text = buildText(jobRole, companyName, packageLpa, driveDate, registrationDeadline, location);

        // Deduplicate by user id even if the resolver returns duplicates.
        Set<Long> seen = new LinkedHashSet<>();
        List<MongoDriveEmailOutbox> batch = new ArrayList<>();
        int skippedInvalid = 0;
        for (DriveRecipientProjection r : recipients) {
            if (r.getUserId() == null || !seen.add(r.getUserId())) {
                continue;
            }
            String email = r.getEmail() == null ? "" : r.getEmail().trim();
            boolean valid = isValidEmail(email);
            if (!valid) {
                skippedInvalid++;
            }
            batch.add(MongoDriveEmailOutbox.builder()
                    .driveId(driveId)
                    .eventKey(EVENT_REGISTRATION_OPEN)
                    .batchId(batchId)
                    .recipientUserId(r.getUserId())
                    .recipientEmail(valid ? email : null)
                    .subject(valid ? subject : null)
                    .html(valid ? html : null)
                    .text(valid ? text : null)
                    .status(valid ? EmailOutboxStatus.PENDING.name()
                            : EmailOutboxStatus.SKIPPED_INVALID_EMAIL.name())
                    .attempts(0)
                    .nextAttemptAt(valid ? now : null)
                    .lastError(valid ? null : "email missing or invalid")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        int queued = insertIgnoringDuplicates(batch);
        log.info("[EMAIL] Drive {} REGISTRATION_OPEN queued={} eligible={} skippedInvalid={} batch={}",
                driveId, queued, recipients.size(), skippedInvalid, batchId);
        return queued;
    }

    /**
     * Number of students the authoritative eligibility rules would notify. Exposes a count
     * only, never addresses, so it is safe to render in the Placement Officer UI.
     */
    public long eligibleRecipientCount(Long driveId) {
        return resolveEligibleRecipients(driveId).size();
    }

    /** Mirrors PlacementDriveService.checkEligibility, resolved in one query per variant. */
    private List<DriveRecipientProjection> resolveEligibleRecipients(Long driveId) {
        EligibilityCriteria criteria = eligibilityRepository.findByPlacementDriveId(driveId).orElse(null);
        if (criteria == null) {
            return userRepository.findDriveRecipientsWithoutCriteria();
        }
        List<Long> allowedDepartments = eligibilityRepository.findAllowedDepartmentIdsByDriveId(driveId);
        boolean allDepartments = allowedDepartments == null || allowedDepartments.isEmpty();
        return userRepository.findDriveRecipientsWithCriteria(
                allDepartments,
                allDepartments ? List.of(-1L) : allowedDepartments,
                criteria.getMinCgpa(),
                criteria.getMaxActiveBacklogs());
    }

    private int insertIgnoringDuplicates(List<MongoDriveEmailOutbox> batch) {
        int inserted = 0;
        for (int start = 0; start < batch.size(); start += INSERT_CHUNK) {
            List<MongoDriveEmailOutbox> chunk = batch.subList(start, Math.min(start + INSERT_CHUNK, batch.size()));
            try {
                mongoTemplate.insert(chunk, MongoDriveEmailOutbox.class);
                inserted += chunk.size();
            } catch (DuplicateKeyException e) {
                for (MongoDriveEmailOutbox doc : chunk) {
                    try {
                        mongoTemplate.insert(doc);
                        inserted++;
                    } catch (DuplicateKeyException alreadyQueued) {
                        // dedupe key (driveId, recipientUserId, eventKey) already exists
                    }
                }
            }
        }
        return inserted;
    }

    public void processDueBatch() {
        if (!props.isEnabled()) {
            return;
        }
        requeueStaleInflight();
        List<MongoDriveEmailOutbox> claimed = claimBatch();
        if (claimed.isEmpty()) {
            return;
        }
        if (!dispatchClient.isConfigured()) {
            for (MongoDriveEmailOutbox job : claimed) {
                markStatus(job, EmailOutboxStatus.CONFIG_ERROR, null, "email provider not configured");
            }
            log.info("[EMAIL] Drive notification batch: sent=0 configError={}", claimed.size());
            return;
        }
        int submitted = 0;
        int failed = 0;
        int retried = 0;
        for (MongoDriveEmailOutbox job : claimed) {
            if (job.getRecipientEmail() == null || job.getRecipientEmail().isBlank()) {
                markStatus(job, EmailOutboxStatus.SKIPPED_INVALID_EMAIL, null, "recipient has no email address");
                failed++;
                continue;
            }
            DriveEmailDraftContent content = new DriveEmailDraftContent(
                    job.getSubject(), job.getHtml(), job.getText());
            if (content.subject() == null || content.html() == null || content.text() == null) {
                markStatus(job, EmailOutboxStatus.FAILED, null, "queued notification is missing its content");
                failed++;
                continue;
            }
            EmailDraft draft = new EmailDraft(job.getDriveId(), job.getRecipientUserId(),
                    job.getRecipientEmail(), content.subject(), content.html(), content.text());
            try {
                markStatus(job, EmailOutboxStatus.SUBMITTED, dispatchClient.send(draft), null);
                submitted++;
            } catch (EmailSendException ex) {
                int used = job.getAttempts() == null ? 1 : job.getAttempts();
                if (ex.isPermanent() || used >= props.getMaxAttempts()) {
                    markStatus(job, EmailOutboxStatus.FAILED,
                            null, sanitize(ex.getCategory().name() + ": " + ex.getMessage()));
                    failed++;
                } else {
                    markForRetry(job);
                    retried++;
                }
            } catch (RuntimeException ex) {
                markStatus(job, EmailOutboxStatus.FAILED,
                        null, sanitize("UNEXPECTED: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                failed++;
            }
        }
        log.info("[EMAIL] Drive notification batch: submitted={} failed={} retried={}", submitted, failed, retried);
    }

    private record DriveEmailDraftContent(String subject, String html, String text) {
    }

    private List<MongoDriveEmailOutbox> claimBatch() {
        Query query = new Query(Criteria.where("status").is(EmailOutboxStatus.PENDING.name())
                .and("nextAttemptAt").lte(LocalDateTime.now()));
        query.with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));
        query.limit(props.getBatchSize());
        List<MongoDriveEmailOutbox> candidates =
                mongoTemplate.find(query, MongoDriveEmailOutbox.class);
        List<MongoDriveEmailOutbox> claimed = new ArrayList<>();
        for (MongoDriveEmailOutbox candidate : candidates) {
            if (candidate.getAttempts() != null && candidate.getAttempts() >= props.getMaxAttempts()) {
                markStatus(candidate, EmailOutboxStatus.FAILED, null, "max attempts reached");
                continue;
            }
            Query claimQuery = new Query(Criteria.where("id").is(candidate.getId())
                    .and("status").is(EmailOutboxStatus.PENDING.name()));
            int nextAttempt = (candidate.getAttempts() == null ? 0 : candidate.getAttempts()) + 1;
            LocalDateTime now = LocalDateTime.now();
            long modified = mongoTemplate.updateFirst(claimQuery, new Update()
                            .set("status", EmailOutboxStatus.IN_FLIGHT.name())
                            .set("attempts", nextAttempt)
                            .set("startedAt", now)
                            .set("updatedAt", now),
                    MongoDriveEmailOutbox.class).getModifiedCount();
            if (modified == 1) {
                candidate.setStatus(EmailOutboxStatus.IN_FLIGHT.name());
                candidate.setAttempts(nextAttempt);
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
                MongoDriveEmailOutbox.class).getModifiedCount();
        if (requeued > 0) {
            log.info("[EMAIL] Requeued {} stale in-flight drive notification jobs", requeued);
        }
    }

    private void markForRetry(MongoDriveEmailOutbox job) {
        LocalDateTime next = LocalDateTime.now().plusSeconds(
                props.getRetryDelayBaseSeconds() * (job.getAttempts() == null ? 0 : job.getAttempts()));
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(job.getId())
                        .and("status").is(EmailOutboxStatus.IN_FLIGHT.name())),
                new Update().set("status", EmailOutboxStatus.PENDING.name())
                        .set("nextAttemptAt", next)
                        .set("startedAt", null)
                        .set("updatedAt", LocalDateTime.now())
                        .set("lastError", "delivery failed, will retry"),
                MongoDriveEmailOutbox.class);
    }

    private void markStatus(MongoDriveEmailOutbox job, EmailOutboxStatus status,
                            String providerMessageId, String lastError) {
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update().set("status", status.name()).set("updatedAt", now);
        if (providerMessageId != null) {
            update.set("providerMessageId", providerMessageId).set("sentAt", now);
        }
        if (lastError != null) {
            update.set("lastError", lastError);
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(job.getId())
                        .and("status").in(EmailOutboxStatus.PENDING.name(), EmailOutboxStatus.IN_FLIGHT.name())),
                update, MongoDriveEmailOutbox.class);
    }

    /** Applies a verified provider delivery event to a drive notification row. */
    public boolean applyDeliveryEvent(String providerMessageId, EmailOutboxStatus status, String eventName) {
        if (providerMessageId == null || providerMessageId.isBlank() || status == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update().set("status", status.name())
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
                update, MongoDriveEmailOutbox.class).getModifiedCount() > 0;
    }

    public DriveEmailStatusResponse statusSummary(Long driveId, String eventKey) {
        DriveEmailStatusResponse.DriveEmailStatusResponseBuilder builder = DriveEmailStatusResponse.builder()
                .driveId(driveId)
                .eventKey(eventKey);
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
                new Document("$match", new Document("driveId", driveId).append("eventKey", eventKey)),
                new Document("$group", new Document("_id", "$status").append("n", new Document("$sum", 1))));
        for (Document doc : mongoTemplate.getCollection("drive_email_outbox").aggregate(pipeline)) {
            long n = ((Number) doc.get("n")).longValue();
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
        return builder.pending(pending)
                .submitted(submitted)
                .delivered(delivered)
                .delayed(delayed)
                .bounced(bounced)
                .complained(complained)
                .suppressed(suppressed)
                .failed(failed)
                .skippedInvalid(skipped)
                .configError(configError)
                .queued(total)
                .total(total)
                .build();
    }

    private String buildHtml(String jobRole, String companyName, BigDecimal packageLpa, LocalDate driveDate,
                             LocalDate registrationDeadline, String location) {
        String link = props.getFrontendUrl().replaceAll("/+$", "") + "/student/drives";
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:auto\">"
                + "<h2 style=\"margin:0 0 4px\">Placement Opportunity</h2>"
                + "<h3 style=\"margin:0 0 16px\">" + escapeHtml(companyName) + " &mdash; " + escapeHtml(jobRole) + "</h3>"
                + "<ul style=\"line-height:1.7;padding-left:20px;margin:0 0 16px\">"
                + detail("Company", companyName)
                + detail("Role", jobRole)
                + detail("CTC / Package", packageLpa == null ? null : packageLpa.toPlainString() + " LPA")
                + detail("Registration deadline", registrationDeadline)
                + detail("Drive date", driveDate)
                + detail("Location", location) + "</ul>"
                + "<p style=\"line-height:1.6\">You are eligible for this drive. "
                + "Register before the deadline to participate.</p>"
                + "<p style=\"margin-top:24px\"><a href=\"" + escapeHtml(link) + "\">"
                + "View Drive</a></p></div>";
    }

    private String detail(String label, Object value) {
        if (value == null || value.toString().isBlank()) {
            return "";
        }
        return "<li><strong>" + label + ":</strong> " + escapeHtml(value.toString()) + "</li>";
    }

    private String buildText(String jobRole, String companyName, BigDecimal packageLpa, LocalDate driveDate,
                             LocalDate registrationDeadline, String location) {
        StringBuilder sb = new StringBuilder("Placement Opportunity\n\n");
        sb.append("Company: ").append(companyName).append('\n');
        sb.append("Role: ").append(jobRole).append('\n');
        if (packageLpa != null) sb.append("CTC / Package: ").append(packageLpa.toPlainString()).append(" LPA\n");
        if (registrationDeadline != null) sb.append("Registration deadline: ").append(registrationDeadline).append('\n');
        if (driveDate != null) sb.append("Drive date: ").append(driveDate).append('\n');
        if (location != null && !location.isBlank()) sb.append("Location: ").append(location).append('\n');
        sb.append("\nYou are eligible for this drive. Register before the deadline to participate.\n");
        sb.append("\nView Drive:\n")
                .append(props.getFrontendUrl().replaceAll("/+$", "")).append("/student/drives\n");
        return sb.toString();
    }

    private boolean isValidEmail(String email) {
        return email != null && !email.isBlank()
                && !CONTROL_CHARS.matcher(email).find()
                && EMAIL_PATTERN.matcher(email).matches();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ").trim();
        return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
    }

    private String trimTo(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
