package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.dashboard.data.ILogs;
import io.kestra.plugin.core.dashboard.data.Logs;
import io.micronaut.data.model.Pageable;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractLogRepositoryTest {
    @Inject
    protected LogRepositoryInterface logRepository;

    protected static LogEntry.LogEntryBuilder logEntry(Level level) {
        return LogEntry.builder()
            .flowId("flowId")
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(IdUtils.create())
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .tenantId(MAIN_TENANT)
            .message("john doe");
    }

    @Test
    void all() {
        LogEntry.LogEntryBuilder builder = logEntry(Level.INFO);

        ArrayListTotal<LogEntry> find = logRepository.find(Pageable.UNPAGED, MAIN_TENANT, null);
        assertThat(find.size()).isZero();


        LogEntry save = logRepository.save(builder.build());
        logRepository.save(builder.executionKind(ExecutionKind.TEST).build()); // should only be loaded by execution id

        find = logRepository.find(Pageable.UNPAGED, MAIN_TENANT, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());
        var filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.MIN_LEVEL)
                .operation(QueryFilter.Op.EQUALS)
                .value(Level.WARN)
                .build(),
            QueryFilter.builder()
                .field(Field.START_DATE)
                .operation(QueryFilter.Op.GREATER_THAN)
                .value(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());
        find = logRepository.find(Pageable.UNPAGED,  "doe", filters);
        assertThat(find.size()).isZero();

        find = logRepository.find(Pageable.UNPAGED, MAIN_TENANT, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        logRepository.find(Pageable.UNPAGED, "kestra-io/kestra", null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        List<LogEntry> list = logRepository.findByExecutionId(MAIN_TENANT, save.getExecutionId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionId(MAIN_TENANT, "io.kestra.unittest", "flowId", save.getExecutionId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskId(MAIN_TENANT, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskId(MAIN_TENANT, "io.kestra.unittest", "flowId", save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskRunId(MAIN_TENANT, save.getExecutionId(), save.getTaskRunId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(MAIN_TENANT, save.getExecutionId(), save.getTaskRunId(), null, 0);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        Integer countDeleted = logRepository.purge(Execution.builder().id(save.getExecutionId()).build());
        assertThat(countDeleted).isEqualTo(2);

        list = logRepository.findByExecutionIdAndTaskId(MAIN_TENANT, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isZero();
    }

    @Test
    void pageable() {
        String executionId = "123";
        LogEntry.LogEntryBuilder builder = logEntry(Level.INFO);
        builder.executionId(executionId);

        for (int i = 0; i < 80; i++) {
            logRepository.save(builder.build());
        }

        builder = logEntry(Level.INFO).executionId(executionId).taskId("taskId2").taskRunId("taskRunId2");
        LogEntry logEntry2 = logRepository.save(builder.build());
        for (int i = 0; i < 20; i++) {
            logRepository.save(builder.build());
        }

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(MAIN_TENANT, executionId, null, Pageable.from(1, 50));

        assertThat(find.size()).isEqualTo(50);
        assertThat(find.getTotal()).isEqualTo(101L);

        find = logRepository.findByExecutionId(MAIN_TENANT, executionId, null, Pageable.from(3, 50));

        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getTotal()).isEqualTo(101L);

        find = logRepository.findByExecutionIdAndTaskId(MAIN_TENANT, executionId, logEntry2.getTaskId(), null, Pageable.from(1, 50));

        assertThat(find.size()).isEqualTo(21);
        assertThat(find.getTotal()).isEqualTo(21L);

        find = logRepository.findByExecutionIdAndTaskRunId(MAIN_TENANT, executionId, logEntry2.getTaskRunId(), null, Pageable.from(1, 10));

        assertThat(find.size()).isEqualTo(10);
        assertThat(find.getTotal()).isEqualTo(21L);

        find = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(MAIN_TENANT, executionId, logEntry2.getTaskRunId(), null, 0, Pageable.from(1, 10));

        assertThat(find.size()).isEqualTo(10);
        assertThat(find.getTotal()).isEqualTo(21L);

        find = logRepository.findByExecutionIdAndTaskRunId(MAIN_TENANT, executionId, logEntry2.getTaskRunId(), null, Pageable.from(10, 10));

        assertThat(find.size()).isZero();
    }

    @Test
    void shouldFindByExecutionIdTestLogs() {
        var builder = logEntry(Level.INFO).executionId("123").executionKind(ExecutionKind.TEST).build();
        logRepository.save(builder);

        List<LogEntry> logs = logRepository.findByExecutionId(MAIN_TENANT, builder.getExecutionId(), null);
        assertThat(logs).hasSize(1);
    }

    @Test
    void delete() {
        LogEntry log1 = logEntry(Level.INFO).build();
        logRepository.save(log1);

        logRepository.deleteByQuery(MAIN_TENANT, log1.getExecutionId(), null, (String) null, null, null);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(MAIN_TENANT, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(MAIN_TENANT, "io.kestra.unittest", "flowId", List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1));

        find = logRepository.findByExecutionId(MAIN_TENANT, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();
    }

    @Test
    void deleteByQuery() {
        LogEntry log1 = logEntry(Level.INFO).build();
        logRepository.save(log1);

        logRepository.deleteByQuery(MAIN_TENANT, log1.getExecutionId(), null, (String) null, null, null);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(MAIN_TENANT, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(MAIN_TENANT, "io.kestra.unittest", "flowId", null);

        find = logRepository.findByExecutionId(MAIN_TENANT, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();
    }

    @Test
    void statistics() throws InterruptedException {
        for (int i = 0; i < 28; i++) {
            logRepository.save(
                logEntry(i < 5 ? Level.TRACE : (i < 8 ? Level.INFO : Level.ERROR))
                    .flowId(i < 15 ? "first" : "second")
                    .build()
            );
        }
        logRepository.save(logEntry(Level.INFO).executionKind(ExecutionKind.TEST).build()); // should be ignored by stats

        // mysql need some time ...
        Thread.sleep(500);

        List<LogStatistics> list = logRepository.statistics(null, MAIN_TENANT, null, "first", null, null, null, null);
        assertThat(list.size()).isEqualTo(31);
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.TRACE) == 5).count()).isEqualTo(1L);
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.INFO) == 3).count()).isEqualTo(1L);
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.ERROR) == 7).count()).isEqualTo(1L);

        list = logRepository.statistics(null, MAIN_TENANT, null, "second", null, null, null, null);
        assertThat(list.size()).isEqualTo(31);
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.ERROR) == 13).count()).isEqualTo(1L);
    }

    @Test
    void findAsync() {
        logRepository.save(logEntry(Level.INFO).build());
        logRepository.save(logEntry(Level.ERROR).build());
        logRepository.save(logEntry(Level.WARN).build());
        logRepository.save(logEntry(Level.INFO).executionKind(ExecutionKind.TEST).build()); // should not be visible here

        ZonedDateTime startDate = ZonedDateTime.now().minusSeconds(1);

        Flux<LogEntry> find = logRepository.findAsync(MAIN_TENANT, "io.kestra.unittest", Level.INFO, startDate);
        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(3);

        find = logRepository.findAsync(MAIN_TENANT, null, Level.ERROR, startDate);
        logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(1);

        find = logRepository.findAsync(MAIN_TENANT, "io.kestra.unused", Level.INFO, startDate);
        logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(0);

        find = logRepository.findAsync(MAIN_TENANT, null, Level.INFO, startDate.plusSeconds(2));
        logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(0);
    }

    @Test
    void findAllAsync() {
        logRepository.save(logEntry(Level.INFO).build());
        logRepository.save(logEntry(Level.INFO).executionKind(ExecutionKind.TEST).build()); // should be present as it's used for backup
        logRepository.save(logEntry(Level.ERROR).build());
        logRepository.save(logEntry(Level.WARN).build());

        Flux<LogEntry> find = logRepository.findAllAsync(MAIN_TENANT);
        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(4);
    }

    @Test
    void fetchData() throws IOException {
        logRepository.save(logEntry(Level.INFO).build());

        var results = logRepository.fetchData(MAIN_TENANT,
            Logs.builder()
                .type(Logs.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Logs.Fields>builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build()
                ))
                .build(),
            ZonedDateTime.now().minusHours(1),
            ZonedDateTime.now(),
            null);

        assertThat(results).hasSize(1);
    }
}
