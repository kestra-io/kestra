package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.QueryFilterTestUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import lombok.Builder;

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
    public TenantService getTenantService() {
        return mock(TenantService.class);
    }

    @Inject
    private TenantService tenantService;

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter() {
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
            GET("/api/v1/" + tenant + "/logs/search?filters[level][GREATER_THAN_OR_EQUAL_TO]=INFO"),
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
        executionRepository.save(
            Execution.builder()
                .id(log1.getExecutionId())
                .namespace("io.kestra.unittest")
                .tenantId(tenant)
                .flowId("full")
                .flowRevision(1)
                .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
                .taskRunList(
                    Collections.singletonList(
                        TaskRun.builder()
                            .id(IdUtils.create())
                            .namespace("io.kestra.unittest")
                            .flowId("full")
                            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
                            .attempts(
                                Collections.singletonList(
                                    TaskRunAttempt.builder()
                                        .build()
                                )
                            )
                            .build()
                    )
                )
                .build()
        );
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

    @Inject
    private LogController logController;

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    @SuppressWarnings("unchecked")
    void listLogsFromExecutionShouldRespectFilters(FiltersTestCase testCase) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        seedLogs(tenant, testCase);

        List<LogEntry> result = client.toBlocking().retrieve(
            GET(filterUri(tenant, testCase.executionId(), "", testCase.filters())),
            Argument.of(List.class, LogEntry.class)
        );

        assertThat(result)
            .extracting(LogEntry::getMessage)
            .containsExactlyInAnyOrderElementsOf(
                testCase.expectedLogs().stream().map(LogEntry::getMessage).toList()
            );
    }

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void downloadLogsFromExecutionShouldRespectFilters(FiltersTestCase testCase) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        seedExecution(tenant, testCase.executionId());
        seedLogs(tenant, testCase);

        String body = client.toBlocking().retrieve(
            GET(filterUri(tenant, testCase.executionId(), "/download", testCase.filters())),
            String.class
        );

        for (LogEntry expected : testCase.expectedLogs()) {
            assertThat(body).contains(expected.getMessage());
        }
        List<LogEntry> excluded = testCase.logs().stream()
            .filter(log -> testCase.expectedLogs().stream().noneMatch(e -> e.getMessage().equals(log.getMessage())))
            .toList();
        for (LogEntry omitted : excluded) {
            assertThat(body).doesNotContain(omitted.getMessage());
        }
    }

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void followLogsFromExecutionShouldReplayHistoricalLogsWithFilters(FiltersTestCase testCase) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        seedLogs(tenant, testCase);

        List<Event<FollowLogEvent>> received = logController
            .followLogsFromExecution(testCase.executionId(), testCase.filters())
            .take(testCase.expectedLogs().size() + 1L) // +1 for the initial "start" event
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(received).isNotNull();
        List<FollowLogEvent> historical = received.stream()
            .filter(event -> "progress".equals(event.getId()))
            .map(Event::getData)
            .toList();

        assertThat(historical)
            .extracting(FollowLogEvent::message)
            .containsExactlyInAnyOrderElementsOf(
                testCase.expectedLogs().stream().map(LogEntry::getMessage).toList()
            );
    }

    private void seedLogs(String tenant, FiltersTestCase testCase) {
        testCase.logs().forEach(log -> logRepository.save(
            log.toBuilder().tenantId(tenant).executionId(testCase.executionId()).build()
        ));
    }

    private void seedExecution(String tenant, String executionId) {
        executionRepository.save(
            Execution.builder()
                .id(executionId)
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
                        .attempts(Collections.singletonList(TaskRunAttempt.builder().build()))
                        .build()
                ))
                .build()
        );
    }

    private static String filterUri(String tenant, String executionId, String suffix, List<QueryFilter> filters) {
        UriBuilder builder = UriBuilder.of("/api/v1/" + tenant + "/logs/" + executionId + suffix);
        QueryFilterTestUtils.toQueryParams(filters).forEach(builder::queryParam);
        return builder.build().toString();
    }

    private static final String TEST_EXECUTION_ID = "exec-filter-test";

    private static final LogEntry traceLog = baseLog(Level.TRACE, "load-data", "task-run-1", 0, "trace line");
    private static final LogEntry debugLog = baseLog(Level.DEBUG, "load-data", "task-run-1", 0, "debug line");
    private static final LogEntry infoLog = baseLog(Level.INFO, "load-data", "task-run-1", 0, "info line");
    private static final LogEntry warnLog = baseLog(Level.WARN, "transform", "task-run-2", 0, "warn line");
    private static final LogEntry errorLog = baseLog(Level.ERROR, "transform", "task-run-2", 1, "error line");
    private static final List<LogEntry> allLogs = List.of(traceLog, debugLog, infoLog, warnLog, errorLog);

    private static final List<FiltersTestCase> filtersTestCases = List.of(
        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(allLogs)
            .filters(List.of())
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(infoLog, warnLog, errorLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(traceLog, debugLog, infoLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(warnLog, errorLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("transform")
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(traceLog, debugLog, infoLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_RUN_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("task-run-1")
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(errorLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.ATTEMPT_NUMBER)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(1)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .executionId(TEST_EXECUTION_ID)
            .logs(allLogs)
            .expectedLogs(List.of(infoLog))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build(),
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("load-data")
                    .build()
            ))
            .build()
    );

    private static LogEntry baseLog(Level level, String taskId, String taskRunId, Integer attempt, String message) {
        return LogEntry.builder()
            .flowId("filter-test-flow")
            .namespace("io.kestra.unittest")
            .taskId(taskId)
            .taskRunId(taskRunId)
            .attemptNumber(attempt)
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .message(message)
            .build();
    }

    @Builder
    private record FiltersTestCase(
        String executionId,
        List<LogEntry> logs,
        List<LogEntry> expectedLogs,
        List<QueryFilter> filters
    ) {
    }
}
