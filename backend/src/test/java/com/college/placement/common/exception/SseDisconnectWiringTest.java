package com.college.placement.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Wires the real {@link GlobalExceptionHandler} into MockMvc and drives the exact failure seen in
 * production: a peer aborts an SSE stream, so the response is already committed as
 * {@code text/event-stream} when the {@link IOException} surfaces.
 *
 * <p>Before this was handled, the generic handler tried to serialise a JSON error body into that
 * committed stream, which raised {@code HttpMessageNotWritableException: No converter for
 * [class java.util.HashMap] with preset Content-Type 'text/event-stream'} and then failed again
 * during the {@code /error} dispatch. The abort therefore logged two ERRORs per closed browser
 * tab. These tests assert the disconnect is now absorbed quietly.
 */
class SseDisconnectWiringTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new StreamController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("an abort on a committed SSE stream is not turned into a 500")
    void abortOnCommittedSseStreamIsNotAnError() throws Exception {
        var result = mockMvc.perform(get("/api/messages/events"))
                .andDo(print())
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("a dropped SSE connection is not a server fault")
                .isNotEqualTo(500);
        assertThat(String.valueOf(result.getResponse().getErrorMessage()))
                .as("no secondary converter failure is reported to the client")
                .doesNotContain("No converter");
    }

    @Test
    @DisplayName("the SSE content type is preserved and not replaced with JSON")
    void sseContentTypeIsPreserved() throws Exception {
        var result = mockMvc.perform(get("/api/messages/events")).andReturn();

        assertThat(result.getResponse().getContentType())
                .as("the handler must not attempt a JSON body on an event-stream response")
                .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    @DisplayName("a normal SSE stream is unaffected")
    void normalSseStreamStillWorks() throws Exception {
        var result = mockMvc.perform(get("/api/messages/healthy")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("data:hello");
    }

    /**
     * Reproduces the server side of an SSE endpoint: commit the event-stream response, then have
     * the peer vanish so the next write raises an {@link IOException}.
     */
    @RestController
    static class StreamController {

        @GetMapping(value = "/api/messages/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter events(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            PrintWriter writer = response.getWriter();
            writer.write("data:connected\n\n");
            writer.flush();
            response.flushBuffer();
            throw new IOException("An established connection was aborted by the software in your host machine");
        }

        @GetMapping(value = "/api/messages/healthy", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter healthy() {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data("hello"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            return emitter;
        }
    }
}
