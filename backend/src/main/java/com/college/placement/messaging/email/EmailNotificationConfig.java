package com.college.placement.messaging.email;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationConfig {

    private final EmailProperties props;

    @PostConstruct
    public void validateEmailConfiguration() {
        if (!props.isEnabled()) {
            log.info("Email notifications: DISABLED (app.email.enabled=false)");
            return;
        }

        if ("resend".equalsIgnoreCase(props.getProvider())) {
            boolean hasKey = props.getResendApiKey() != null && !props.getResendApiKey().isBlank();
            boolean hasFrom = props.getFromEmail() != null && !props.getFromEmail().isBlank();

            if (hasKey && hasFrom) {
                log.info("Resend email: ENABLED | From: {} <{}>", props.getFromName(), props.getFromEmail());
            } else {
                log.warn("Resend email: MISCONFIGURED | Missing apiKey: {}, Missing fromEmail: {}", !hasKey, !hasFrom);
            }
        } else {
            log.info("Email notifications: ENABLED (provider: {})", props.getProvider());
        }
    }
}