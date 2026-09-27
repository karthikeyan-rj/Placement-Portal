package com.college.placement.messaging.email;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailOutboxWorker {

    private final EmailNotificationService emailNotificationService;
    private final DriveEmailNotificationService driveEmailNotificationService;
    private final EmailProperties props;

    private ScheduledExecutorService executor;

    @PostConstruct
    void start() {
        if (!props.isEnabled()) {
            log.info("[EMAIL] Outbox worker disabled (app.email.enabled=false)");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "email-outbox-worker");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick,
                props.getWorkerIntervalMs(), props.getWorkerIntervalMs(), TimeUnit.MILLISECONDS);
        log.info("[EMAIL] Outbox worker started (interval={}ms, batchSize={}, provider={})",
                props.getWorkerIntervalMs(), props.getBatchSize(), props.getProvider());
    }

    private void tick() {
        try {
            emailNotificationService.processDueBatch();
        } catch (Exception e) {
            log.warn("[EMAIL] Outbox worker cycle failed: {}", String.valueOf(e.getMessage()).replaceAll("[\\r\\n]", " "));
        }
        try {
            driveEmailNotificationService.processDueBatch();
        } catch (Exception e) {
            log.warn("[EMAIL] Drive outbox worker cycle failed: {}", String.valueOf(e.getMessage()).replaceAll("[\\r\\n]", " "));
        }
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}