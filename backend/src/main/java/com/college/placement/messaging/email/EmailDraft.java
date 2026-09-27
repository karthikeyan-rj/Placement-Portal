package com.college.placement.messaging.email;

public record EmailDraft(Long messageId, Long recipientUserId, String toEmail,
                         String subject, String html, String text) {
}