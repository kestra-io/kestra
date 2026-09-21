package io.kestra.webserver.controllers.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.assertions.Problems;
import io.kestra.webserver.errors.ProblemDetail;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class ErrorControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    private static InMemoryAppender appender;

    @BeforeAll
    static void setupLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new InMemoryAppender();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void clearLogs() {
        appender.clear();
    }

    @Test
    void shouldReportValidationFailedWithFieldErrorsWhenPluginTypeIsUnknown() throws JsonProcessingException {
        // Given a flow whose task declares a plugin type that does not exist
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "tasks", Collections.singletonList(
                ImmutableMap.of("id", IdUtils.create(), "type", "io.kestra.invalid")
            )
        );

        // When it is created
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(postFlow(flow))
        );

        // Then the problem names the failure and the offending type, and locates it
        ProblemDetail problem = Problems.assertProblem(exception, ProblemTypes.VALIDATION_FAILED);
        assertThat(problem.detail()).contains("io.kestra.invalid");
        assertThat(problem.instance()).isEqualTo("/api/v1/main/flows");
        Problems.assertErrors(exception)
            .isNotEmpty()
            .anySatisfy(error -> {
                assertThat(error.detail()).contains("io.kestra.invalid");
                assertThat(error.pointer()).isNotNull();
            });
    }

    @Test
    void shouldReportValidationFailedWithFieldErrorsWhenPropertyIsUnknown() throws JsonProcessingException {
        // Given a flow carrying a property the model does not declare
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "unknown", "properties",
            "tasks", Collections.singletonList(
                ImmutableMap.of("id", IdUtils.create(), "type", Log.class.getName(), "message", "logging")
            )
        );

        // When it is created
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(postFlow(flow))
        );

        // Then the unknown property is named
        Problems.assertProblem(exception, ProblemTypes.VALIDATION_FAILED);
        Problems.assertErrors(exception)
            .isNotEmpty()
            .anySatisfy(error -> assertThat(error.detail()).contains("unknown"));
    }

    @Test
    void shouldNotLeakInternalClassNamesWhenBodyCannotBeDecoded() {
        // When a body of the wrong shape is posted (kestra-ee#10266)
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/main/triggers/backfill/delete", "\"hello\"").contentType(MediaType.APPLICATION_JSON)
            )
        );

        // Then nothing in the document names the target DTO, and it is reported as a client error
        String body = exception.getResponse().getBody(String.class).orElseThrow();
        assertThat(body).doesNotContain("ApiTriggerId");
        assertThat(body).doesNotContain(ProblemTypes.INTERNAL_ERROR.title());
        assertThat(exception.getStatus().getCode()).isBetween(400, 499);
    }

    @Test
    void shouldNotLogWhenClientError() {
        // When a route rejects the request
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/test-utils/failing-with-400-client-error"))
        );

        // Then the caller is told exactly what went wrong, and nothing is logged as a server error
        ProblemDetail problem = Problems.assertProblem(exception, ProblemTypes.BAD_REQUEST);
        assertThat(problem.detail()).isEqualTo("a client error message");

        assertThat(hasErrorLogContaining("a client error message"))
            .withFailMessage("A client error must not be logged at ERROR")
            .isFalse();
    }

    @Test
    void shouldHideMessageButLogItWithTraceIdWhenServerError() {
        // When a route fails unexpectedly
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/test-utils/failing-with-500-server-error"))
        );

        // Then the caller gets a fixed, non-revealing detail plus a correlation id
        ProblemDetail problem = Problems.assertProblem(exception, ProblemTypes.INTERNAL_ERROR);
        assertThat(problem.detail())
            .withFailMessage("A server error must not echo its exception message")
            .doesNotContain("an unhandled server error message");
        assertThat(problem.traceId()).isNotBlank();
        assertThat(problem.errors()).isNullOrEmpty();

        // And the real message reached the log, correlated by that same id
        boolean logged = appender.getLogs().stream().anyMatch(log ->
            Level.ERROR == log.getLevel()
                && log.getFormattedMessage().contains("an unhandled server error message")
                && log.getFormattedMessage().contains(problem.traceId())
        );
        assertThat(logged)
            .withFailMessage("The exception message must reach the log under the traceId returned to the caller")
            .isTrue();
    }

    @Test
    void shouldStillLogWhenServerErrorHasNoMessage() {
        // When a route throws an exception carrying no message
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/test-utils/failing-with-server-error-with-no-error-message"))
        );

        // Then the response is still a complete problem document
        ProblemDetail problem = Problems.assertProblem(exception, ProblemTypes.INTERNAL_ERROR);
        assertThat(problem.detail()).isNotBlank();

        // And the throwable is still logged so the cause is recoverable
        boolean logged = appender.getLogs().stream().anyMatch(log ->
            Level.ERROR == log.getLevel()
                && log.getFormattedMessage().contains(problem.traceId())
                && log.getThrowableProxy() != null
                && "java.lang.NullPointerException".equals(log.getThrowableProxy().getClassName())
        );
        assertThat(logged).withFailMessage("Expected the NullPointerException to be logged").isTrue();
    }

    @Test
    void shouldReportNotFoundWhenNoRouteMatches() {
        // When a request matches no route at all, so there is no exception to map
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/there-is-no-such-route"))
        );

        // Then it is still a problem document rather than an empty body
        ProblemDetail problem = Problems.assertProblem(exception, ProblemTypes.NOT_FOUND);
        assertThat(problem.detail()).isNotBlank();
        assertThat(problem.instance()).isEqualTo("/api/v1/main/there-is-no-such-route");
    }

    @Test
    void shouldReportMethodNotAllowedWithAllowHeaderWhenMethodIsWrong() {
        // When a known route is called with the wrong method
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST("/test-utils/failing-with-400-client-error", ""))
        );

        // Then the problem says so, and the response still advertises what would have worked
        Problems.assertProblem(exception, ProblemTypes.METHOD_NOT_ALLOWED);
        assertThat(exception.getResponse().getHeaders().get("Allow")).contains("GET");
    }

    private static io.micronaut.http.HttpRequest<String> postFlow(Map<String, Object> flow) throws JsonProcessingException {
        return POST("/api/v1/main/flows", JacksonMapper.ofYaml().writeValueAsString(flow))
            .contentType(MediaType.APPLICATION_YAML_TYPE);
    }

    private static boolean hasErrorLogContaining(String text) {
        return appender.getLogs()
            .stream()
            .anyMatch(log -> Level.ERROR == log.getLevel() && log.getFormattedMessage().contains(text));
    }

    private static class InMemoryAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> logs = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            logs.add(event);
        }

        public List<ILoggingEvent> getLogs() {
            return logs;
        }

        public void clear() {
            logs.clear();
        }
    }
}
