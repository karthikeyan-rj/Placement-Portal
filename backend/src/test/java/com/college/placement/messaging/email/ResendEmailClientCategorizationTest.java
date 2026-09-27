package com.college.placement.messaging.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests for the Resend failure taxonomy.
 *
 * <p>History: {@code categorize} used to route every non-401/403/429/5xx response through
 * {@code Category.valueOf("ERROR")}, but the enum has no ERROR constant. An unverified
 * sender domain answers 422, so the classification itself threw IllegalArgumentException,
 * escaped the worker's EmailSendException handler, aborted the remaining batch and left
 * every claimed job stranded as IN_FLIGHT. The first test below pins that defect shut.
 */
class ResendEmailClientCategorizationTest {

    private final ResendEmailClient client = new ResendEmailClient(new EmailProperties(), new ObjectMapper());

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 410, 415, 422})
    @DisplayName("client errors are permanent and must not be retried")
    void clientErrorsArePermanent(int status) {
        EmailSendException ex = client.categorize(status, "{}");
        assertThat(ex.isPermanent())
                .as("status %s must not be retried", status)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504})
    @DisplayName("transient failures stay retryable")
    void transientFailuresAreRetryable(int status) {
        assertThat(client.categorize(status, "{}").isPermanent())
                .as("status %s must remain retryable", status)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 410, 415, 422, 429, 500, 502, 503})
    @DisplayName("classification never throws a non-EmailSendException for any provider status")
    void classificationAlwaysYieldsEmailSendException(int status) {
        assertThatCode(() -> client.categorize(status, "{}")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unverified sender domain (422) is reported as a permanent invalid request")
    void unverifiedDomainIsPermanentInvalidRequest() {
        EmailSendException ex = client.categorize(422,
                "{\"statusCode\":422,\"name\":\"validation_error\","
                        + "\"message\":\"The domain placements@yourdomain.com is not verified\"}");
        assertThat(ex.getCategory()).isEqualTo(EmailSendException.Category.INVALID_REQUEST);
        assertThat(ex.isPermanent()).isTrue();
        assertThat(ex.getMessage()).contains("validation_error").contains("status=422");
    }

    @Test
    @DisplayName("restricted key (401) is permanent so it is not retried three times")
    void restrictedKeyIsPermanentAuth() {
        EmailSendException ex = client.categorize(401,
                "{\"statusCode\":401,\"name\":\"restricted_api_key\","
                        + "\"message\":\"This API key is restricted to only send emails\"}");
        assertThat(ex.getCategory()).isEqualTo(EmailSendException.Category.AUTH);
        assertThat(ex.isPermanent()).isTrue();
    }

    @Test
    @DisplayName("provider error name is reported but free-text message content is not persisted")
    void errorNameIsIncludedWithoutLeakingMessageBody() {
        EmailSendException ex = client.categorize(422,
                "{\"name\":\"missing_from_field\",\"message\":\"secret payload abc123\"}");
        assertThat(ex.getMessage()).contains("missing_from_field");
        assertThat(ex.getMessage()).doesNotContain("secret payload");
    }

    @Test
    @DisplayName("a non-JSON error body still yields a usable classification")
    void nonJsonErrorBodyIsTolerated() {
        assertThatCode(() -> client.categorize(500, "<html>gateway error</html>"))
                .doesNotThrowAnyException();
        assertThat(client.categorize(500, "<html>gateway error</html>").isPermanent()).isFalse();
    }

    @Test
    @DisplayName("send() refuses to call the provider when the key or sender is missing")
    void unconfiguredClientFailsFast() {
        EmailProperties blank = new EmailProperties();
        blank.setResendApiKey("");
        blank.setFromEmail("");
        ResendEmailClient unconfigured = new ResendEmailClient(blank, new ObjectMapper());
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatCode(() -> unconfigured.send(
                new EmailDraft(1L, 2L, "a@b.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("EMAIL_NOT_CONFIGURED");
    }
}
