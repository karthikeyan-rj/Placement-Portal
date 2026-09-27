package com.college.placement.messaging.email;

import com.college.placement.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final EmailDispatchClient dispatchClient;
    private final EmailProperties props;

    /**
     * Synchronously sends a 6-digit registration verification code via the configured email transport.
     * Throws BadRequestException if delivery fails so that the user receives an immediate clear error.
     * Code value is NEVER logged.
     */
    public String sendRegistrationCode(String toEmail, String code) {
        String subject = "Your Placement Portal verification code";

        String textBody = """
                Placement Portal

                Your verification code is:
                %s

                This code expires in 10 minutes.

                If you did not request this code, you can ignore this email.
                """.formatted(code);

        String htmlBody = """
                <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #1e293b; margin-top: 0;">Placement Portal</h2>
                    <p style="color: #475569; font-size: 15px;">Your verification code is:</p>
                    <div style="background-color: #f1f5f9; padding: 16px; border-radius: 6px; text-align: center; margin: 20px 0;">
                        <span style="font-size: 28px; font-weight: bold; letter-spacing: 6px; color: #0f172a;">%s</span>
                    </div>
                    <p style="color: #64748b; font-size: 13px;">This code expires in 10 minutes.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="color: #94a3b8; font-size: 12px; margin-bottom: 0;">If you did not request this code, you can safely ignore this email.</p>
                </div>
                """.formatted(code);

        EmailDraft draft = new EmailDraft(null, null, toEmail, subject, htmlBody, textBody);
        try {
            log.info("[EMAIL] Sending registration verification code to recipient: {}", toEmail);
            String providerId = dispatchClient.send(draft);
            log.info("[EMAIL] Registration verification code sent successfully. Provider ID: {}", providerId);
            return providerId;
        } catch (EmailSendException e) {
            log.error("[EMAIL] Failed to send registration verification code to {}: category={}, permanent={}",
                    toEmail, e.getCategory(), e.isPermanent());
            throw new BadRequestException("We couldn't send the verification email. Please try again.");
        } catch (Exception e) {
            log.error("[EMAIL] Unexpected error sending verification code to {}", toEmail, e);
            throw new BadRequestException("We couldn't send the verification email. Please try again.");
        }
    }

    /**
     * Sends a direct test email via the configured transport.
     */
    public String sendTestEmail(String toEmail, String subject, String body) {
        EmailDraft draft = new EmailDraft(
                null,
                null,
                toEmail,
                subject,
                "<p>" + body.replace("\n", "<br/>") + "</p>",
                body
        );
        try {
            return dispatchClient.send(draft);
        } catch (EmailSendException e) {
            log.error("[EMAIL] Direct test email failed: {}", e.getMessage());
            throw new BadRequestException("Test email send failed: " + e.getMessage());
        }
    }
}
