package com.college.placement.messaging.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private boolean enabled = false;

    private String provider = "mock";

    private String resendApiKey = "";

    private String fromEmail = "placements@localhost.local";

    private String fromName = "Placement Portal";

    private String replyTo = "";

    private String frontendUrl = "http://localhost:5173";

    private int batchSize = 50;

    private long workerIntervalMs = 2000;

    private int maxAttempts = 3;

    private long retryDelayBaseSeconds = 60;

    private long inflightTimeoutSeconds = 300;

    private long connectTimeoutSeconds = 10;

    private long requestTimeoutSeconds = 15;

    /** Svix/Resend signing secret used to verify inbound webhook authenticity. */
    private String webhookSecret = "";

    /** Reject webhook timestamps older than this to block replay. */
    private long webhookToleranceSeconds = 300;

    private MockFailureMode mockFailure = MockFailureMode.NONE;
}