package io.kestra.webserver.controllers.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("OptionalGetWithoutIsPresent")
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
    void type() {
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", "io.kestra.invalid"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Argument.of(Flow.class), Argument.of(Object.class))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Invalid type: io.kestra.invalid");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.Flow[\\\"tasks\\\"] > java.util.ArrayList[0]\"");
        assertThat(response).contains("Failed to convert argument");

        // missing getter & setter on JsonError
        // assertThat(exception.getResponse().getBody(JsonError.class).get().getEmbedded().get("errors").get().getFirst().getPath(), containsInAnyOrder("tasks"));
    }

    @Test
    void unknownProperties() {
        Map<String, Object> flow =  ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "unknown", "properties",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", Log.class.getName(),
                "message", "logging"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                POST("/api/v1/main/flows", JacksonMapper.ofYaml().writeValueAsString(flow)).contentType(MediaType.APPLICATION_YAML),
                Argument.of(String.class),
                Argument.of(JsonError.class)
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Invalid entity: Unrecognized field \\\"unknown\\\" (class io.kestra.core.models.flows.FlowWithSource), not marked as ignorable");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.FlowWithSource[\\\"unknown\\\"]\"");
    }

    @Test
    void clientError400() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/test-utils/failing-with-400-client-error")
        ));

        assertThat(exception.getStatus().getCode()).isEqualTo(BAD_REQUEST.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("a client error message");

        boolean foundAMatchingErrorLog = appender.getLogs().stream()
            .anyMatch(log -> log.getLevel() == Level.ERROR &&
                log.getFormattedMessage().contains("a client error message"));
        assertThat(foundAMatchingErrorLog).withFailMessage("Expected no logs for a client error").isEqualTo(false);
    }

    @Test
    void clientError500() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/test-utils/failing-with-500-server-error")
        ));

        assertThat(exception.getStatus().getCode()).isEqualTo(INTERNAL_SERVER_ERROR.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("an unhandled server error message");

        boolean foundAMatchingErrorLog = appender.getLogs().stream()
            .anyMatch(log -> log.getLevel() == Level.ERROR &&
                log.getFormattedMessage().contains("an unhandled server error message"));
        assertThat(foundAMatchingErrorLog).withFailMessage("Expected a log for a server error").isEqualTo(true);
    }

    @Test
    void clientError500_withNoErrorMessage() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/test-utils/failing-with-server-error-with-no-error-message")
        ));

        boolean foundAMatchingErrorLog = appender.getLogs().stream()
            .anyMatch(log -> log.getLevel() == Level.ERROR &&
                log.getFormattedMessage().contains("Server error") && log.getThrowableProxy().getClassName().equals("java.lang.NullPointerException"));
        assertThat(foundAMatchingErrorLog).withFailMessage("Expected error log not found").isEqualTo(true);
    }

    @Disabled("Test disabled: no exception thrown when converting to dynamic properties")
    @Test
    void invalidEnum() {
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", Log.class.getName(),
                "message", "Yeah !",
                "level", "WRONG"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Argument.of(Flow.class), Argument.of(JsonError.class))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Cannot deserialize value of type `org.slf4j.event.Level` from String \\\"WRONG\\\"");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.Flow[\\\"tasks\\\"] > java.util.ArrayList[0] > io.kestra.plugin.core.log.Log[\\\"level\\\"]\"");
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