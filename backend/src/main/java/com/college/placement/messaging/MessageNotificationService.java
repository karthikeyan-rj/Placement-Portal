package com.college.placement.messaging;

import com.college.placement.messaging.dto.MessageNotification;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks authenticated server-sent-event subscribers per user and fans out
 * lightweight "NEW_MESSAGE" notifications. One user may have many concurrent
 * connections (multiple tabs/devices); events are only sent to the connections
 * of the authorized recipients.
 */
@Service
@Slf4j
public class MessageNotificationService {

    private static final long KEEPALIVE_MS = 20_000L;

    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();
    private ScheduledExecutorService keepaliveExecutor;

    @PostConstruct
    void startKeepalive() {
        keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "msg-sse-keepalive");
            t.setDaemon(true);
            return t;
        });
        keepaliveExecutor.scheduleAtFixedRate(this::sendKeepalive, KEEPALIVE_MS, KEEPALIVE_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (keepaliveExecutor != null) {
            keepaliveExecutor.shutdownNow();
        }
        for (Set<SseEmitter> set : connections.values()) {
            for (SseEmitter emitter : set) {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                }
            }
        }
        connections.clear();
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        connections.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, emitter));
        send(userId, emitter, SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        return emitter;
    }

    public void publishNewMessage(Collection<Long> recipientIds, MessageNotification payload) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }
        for (Long userId : recipientIds) {
            Set<SseEmitter> set = connections.get(userId);
            if (set == null || set.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : set) {
                send(userId, emitter,
                        SseEmitter.event().name("NEW_MESSAGE").data(payload, MediaType.APPLICATION_JSON));
            }
        }
    }

    private void sendKeepalive() {
        for (Map.Entry<Long, Set<SseEmitter>> entry : connections.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                send(entry.getKey(), emitter, SseEmitter.event().comment("keepalive"));
            }
        }
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception ex) {
            // Client went away (or the write failed): drop this connection. A
            // failed flush must not propagate as an async error dispatch.
            remove(userId, emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = connections.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                connections.remove(userId, set);
            }
        }
    }
}
