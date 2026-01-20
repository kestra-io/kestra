package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.tenants.TenantValidationFilter;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class LogControllerTest {

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @MockBean(TenantService.class)
    public TenantService getTenantService(){
        return mock(TenantService.class);
    }
    @Inject
    private TenantService tenantService;

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter(){
        return mock(TenantValidationFilter.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchLogs() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO);
        LogEntry log2 = logEntry(tenant, Level.WARN);
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        PagedResults<LogEntry> logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/search"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal()).isEqualTo(3L);

        logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/search?filters[level][EQUALS]=INFO"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal()).isEqualTo(2L);

        // Test with old parameters
        logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/search?minLevel=INFO"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal()).isEqualTo(2L);


        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/" + tenant + "/logs/search?page=1&size=-1"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/" + tenant + "/logs/search?page=0"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchLogsByExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").build();
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        List<LogEntry> logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/" + log1.getExecutionId()),
            Argument.of(List.class, LogEntry.class)
        );
        assertThat(logs.size()).isEqualTo(2);
        assertThat(logs.getFirst().getExecutionId()).isEqualTo(log1.getExecutionId());
        assertThat(logs.get(1).getExecutionId()).isEqualTo(log1.getExecutionId());
    }

    @Test
    void downloadLogsFromExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO);
        executionRepository.save(Execution.builder()
            .id(log1.getExecutionId())
            .namespace("io.kestra.unittest")
            .tenantId(tenant)
            .flowId("full")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .taskRunList(Collections.singletonList(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("full")
                    .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
                    .attempts(Collections.singletonList(
                        TaskRunAttempt.builder()
                            .build()
                    ))
                    .build()
            ))
            .build());
        LogEntry log2 = log1.toBuilder().message("another message").build();
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        String logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/" + log1.getExecutionId() + "/download"),
            String.class
        );
        assertThat(logs).contains("john doe");
        assertThat(logs).contains("another message");
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteLogsFromExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").build();
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        HttpResponse<?> delete = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/" + tenant + "/logs/" + log1.getExecutionId())
        );
        assertThat(delete.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        List<LogEntry> logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/" + log1.getExecutionId()),
            Argument.of(List.class, LogEntry.class)
        );
        assertThat(logs.size()).isZero();
    }

    @Test
    void deleteLogsFromExecutionByQuery() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO);
        LogEntry log2 = log1.toBuilder().message("another message").build();
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);

        HttpResponse<?> delete = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/" + tenant + "/logs/" + log1.getNamespace() + "/" + log1.getFlowId())
        );
        assertThat(delete.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        List<LogEntry> logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/" + log1.getExecutionId()),
            Argument.of(List.class, LogEntry.class)
        );
        assertThat(logs.size()).isZero();
    }

    @Test
    void searchLogsFilteredByDate() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        LogEntry log1 = logEntry(tenant, Level.INFO, Instant.now().minus(2, ChronoUnit.DAYS));
        LogEntry log2 = logEntry(tenant, Level.WARN, Instant.now().minus(1, ChronoUnit.DAYS));
        LogEntry log3 = logEntry(tenant, Level.DEBUG);
        logRepository.save(log1);
        logRepository.save(log2);
        logRepository.save(log3);


        PagedResults<LogEntry> logs = client.toBlocking().retrieve(
            GET("/api/v1/" + tenant + "/logs/search?filters[timeRange][EQUALS]=PT25H"),
            Argument.of(PagedResults.class, LogEntry.class)
        );
        assertThat(logs.getTotal()).isEqualTo(2L);
    }

    private static LogEntry logEntry(String tenant, Level level) {
        return logEntry(tenant, level, Instant.now());
    }

    private static LogEntry logEntry(String tenant, Level level, Instant timestamp) {
        return LogEntry.builder()
            .tenantId(tenant)
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(IdUtils.create())
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(timestamp)
            .level(level)
            .thread("")
            .message("john doe")
            .build();
    }
}
