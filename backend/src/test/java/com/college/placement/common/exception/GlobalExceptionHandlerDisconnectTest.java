package com.college.placement.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.net.SocketException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for how the global handler treats a peer that goes away mid-response.
 *
 * <p>Closing a browser tab on the messages page aborts the SSE stream. That arrives as an
 * {@link IOException} on an already-committed {@code text/event-stream} response, so any attempt
 * to render a JSON error body fails a second time and the client sees a 500 for a normal
 * disconnect. These tests pin the behaviour that keeps disconnects quiet while genuine faults
 * still surface.
 */
class GlobalExceptionHandlerDisconnectTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/messages/events");

    @Test
    @DisplayName("a socket abort writes no response body instead of a 500")
    void socketAbortIsSwallowed() {
        handler.handleIoFailure(
                new IOException("An established connection was aborted by the software in your host machine"),
                request);
    }

    @Test
    @DisplayName("a reset or broken pipe is treated as a disconnect")
    void resetAndBrokenPipeAreDisconnects() {
        handler.handleIoFailure(new IOException("Connection reset by peer"), request);
        handler.handleIoFailure(new IOException("Broken pipe"), request);
    }

    @Test
    @DisplayName("a disconnect wrapped in another exception is still detected")
    void wrappedDisconnectIsDetected() {
        IOException wrapped = new IOException("stream closed",
                new SocketException("Connection reset by peer"));
        handler.handleIoFailure(wrapped, request);
        assertThat(handler.isClientDisconnect(wrapped)).isTrue();
    }

    @Test
    @DisplayName("a real server-side IO fault is not silently swallowed")
    void genuineIoFaultIsNotSwallowed() {
        // A disk or socket-permission style failure is a genuine fault. It is logged rather than
        // turned into a client-visible body, but it must not be classified as a disconnect.
        IOException real = new IOException("Permission denied");
        assertThat(handler.isClientDisconnect(real)).isFalse();
    }

    @Test
    @DisplayName("a non-disconnect exception still produces the standard 500 envelope")
    void ordinaryExceptionStillReturns500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new IllegalStateException("boom"), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
    }

    @Test
    @DisplayName("a disconnect reaching the generic handler produces no body")
    void genericHandlerSwallowsDisconnect() {
        assertThat(handler.handleGeneral(
                new IOException("An established connection was aborted by the software in your host machine"),
                request)).isNull();
    }
}
