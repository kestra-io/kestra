package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class LogControllerTest extends JdbcH2ControllerTest {

    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    @Client("/")
    ReactorSseClient sseClient;

    @Test
    void find() {
        LogEntry log1 = logEntry(Level.INFO);
        LogEntry log2 = logEntry(Level.WARN);
        LogEntry log3 = logEntry(Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        PagedResults<LogEntry> logs = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/logs/search"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal(), is(3L));

        logs = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/logs/search?minLevel=INFO"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal(), is(2L));
    }

    @Test
    void findByExecution() {
        LogEntry log1 = logEntry(Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").id(IdUtils.create()).build();
        LogEntry log3 = logEntry(Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        List<LogEntry> logs = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/logs/" + log1.getExecutionId()),
            Argument.of(List.class, LogEntry.class)
        );
        assertThat(logs.size(), is(2));
        assertThat(logs.get(0).getExecutionId(), is(log1.getExecutionId()));
        assertThat(logs.get(1).getExecutionId(), is(log1.getExecutionId()));
    }

    @Test
    void download() {
        LogEntry log1 = logEntry(Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").id(IdUtils.create()).build();
        LogEntry log3 = logEntry(Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        String logs = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/logs/" + log1.getExecutionId() + "/download"),
            String.class
        );
        assertThat(logs, containsString("john doe"));
        assertThat(logs, containsString("another message"));
    }

    @Test
    void delete() {
        LogEntry log1 = logEntry(Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").id(IdUtils.create()).build();
        LogEntry log3 = logEntry(Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        HttpResponse<?> delete = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/logs/" + log1.getExecutionId())
        );
        assertThat(delete.getStatus(), is(HttpStatus.OK));

        List<LogEntry> logs = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/logs/" + log1.getExecutionId()),
            Argument.of(List.class, LogEntry.class)
        );
        assertThat(logs.size(), is(0));
    }

    private static LogEntry logEntry(Level level) {
        return LogEntry.builder()
            .id(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(IdUtils.create())
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .message("john doe")
            .build();
    }
}