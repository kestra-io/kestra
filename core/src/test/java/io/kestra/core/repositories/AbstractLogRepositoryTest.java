package io.kestra.core.repositories;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.dashboard.data.Logs;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
import static io.kestra.core.models.flows.FlowScope.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
public abstract class AbstractLogRepositoryTest {
    @Inject
    protected LogRepositoryInterface logRepository;

    protected static LogEntry.LogEntryBuilder logEntry(String tenantId, Level level) {
        return logEntry(tenantId, level, IdUtils.create());
    }

    protected static LogEntry.LogEntryBuilder logEntry(String tenantId, Level level, String executionId) {
        return LogEntry.builder()
            .flowId("flowId")
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(executionId)
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .tenantId(tenantId)
            .triggerId("triggerId")
            .message("john doe");
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        ArrayListTotal<LogEntry> entries = logRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

        assertThat(entries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_async(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        Flux<LogEntry> find = logRepository.findAsync(tenant, List.of(filter));

        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_delete_with_filter(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        logRepository.deleteByFilters(tenant, List.of(filter));

        assertThat(logRepository.findAllAsync(tenant).collectList().block()).isEmpty();
    }



    static Stream<QueryFilter> filterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.QUERY).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.QUERY).value("anotherId").operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.SCOPE).value(List.of(USER)).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.SCOPE).value(List.of(SYSTEM)).operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.unittest").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("another.namespace").operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("kestra").operation(Op.CONTAINS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra").operation(Op.STARTS_WITH).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("unittest").operation(Op.ENDS_WITH).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value(".*kestra.*").operation(Op.REGEX).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value(List.of("io.kestra.unittest")).operation(Op.IN).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value(List.of("another.namespace")).operation(Op.NOT_IN).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("io").operation(Op.PREFIX).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("anotherFlowId").operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("lowI").operation(Op.CONTAINS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("flow").operation(Op.STARTS_WITH).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("Id").operation(Op.ENDS_WITH).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value(".lowI.").operation(Op.REGEX).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("triggerId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("anotherId").operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("igger").operation(Op.CONTAINS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("trigger").operation(Op.STARTS_WITH).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("Id").operation(Op.ENDS_WITH).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value(List.of("triggerId")).operation(Op.IN).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value(List.of("anotherId")).operation(Op.NOT_IN).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("executionId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("anotherId").operation(Op.NOT_EQUALS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("xecution").operation(Op.CONTAINS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("execution").operation(Op.STARTS_WITH).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("Id").operation(Op.ENDS_WITH).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value(List.of("executionId")).operation(Op.IN).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value(List.of("anotherId")).operation(Op.NOT_IN).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.DEBUG).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.ERROR).operation(Op.NOT_EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter){
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> logRepository.find(
                Pageable.UNPAGED,
                TestsUtils.randomTenant(this.getClass().getSimpleName()),
                List.of(filter)));

    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.STATE).value(State.Type.RUNNING).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build()
        );
    }

    @Test
    void all() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        LogEntry.LogEntryBuilder builder = logEntry(tenant, Level.INFO);

        ArrayListTotal<LogEntry> find = logRepository.find(Pageable.UNPAGED, tenant, null);
        assertThat(find.size()).isZero();


        LogEntry save = logRepository.save(builder.build());
        logRepository.save(builder.executionKind(ExecutionKind.TEST).build()); // should only be loaded by execution id

        find = logRepository.find(Pageable.UNPAGED, tenant, null);
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

        find = logRepository.find(Pageable.UNPAGED, tenant, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        logRepository.find(Pageable.UNPAGED, "kestra-io/kestra", null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        List<LogEntry> list = logRepository.findByExecutionId(tenant, save.getExecutionId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionId(tenant, "io.kestra.unittest", "flowId", save.getExecutionId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskId(tenant, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskId(tenant, "io.kestra.unittest", "flowId", save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskRunId(tenant, save.getExecutionId(), save.getTaskRunId(), null);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        list = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenant, save.getExecutionId(), save.getTaskRunId(), null, 0);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        Integer countDeleted = logRepository.purge(Execution.builder().id(save.getExecutionId()).build());
        assertThat(countDeleted).isEqualTo(2);

        list = logRepository.findByExecutionIdAndTaskId(tenant, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size()).isZero();
    }

    @Test
    void pageable() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = "123";
        LogEntry.LogEntryBuilder builder = logEntry(tenant, Level.INFO);
        builder.executionId(executionId);

        for (int i = 0; i < 80; i++) {
            logRepository.save(builder.build());
        }

        builder = logEntry(tenant, Level.INFO).executionId(executionId).taskId("taskId2").taskRunId("taskRunId2");
        LogEntry logEntry2 = logRepository.save(builder.build());
        for (int i = 0; i < 20; i++) {
            logRepository.save(builder.build());
        }
        // normal kind should also be retrieved
        logRepository.save(builder.executionKind(ExecutionKind.NORMAL).build());

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(tenant, executionId, null, Pageable.from(1, 50));

        assertThat(find.size()).isEqualTo(50);
        assertThat(find.getTotal()).isEqualTo(102L);

        find = logRepository.findByExecutionId(tenant, executionId, null, Pageable.from(3, 50));

        assertThat(find.size()).isEqualTo(2);
        assertThat(find.getTotal()).isEqualTo(102L);

        find = logRepository.findByExecutionIdAndTaskId(tenant, executionId, logEntry2.getTaskId(), null, Pageable.from(1, 50));

        assertThat(find.size()).isEqualTo(22);
        assertThat(find.getTotal()).isEqualTo(22L);

        find = logRepository.findByExecutionIdAndTaskRunId(tenant, executionId, logEntry2.getTaskRunId(), null, Pageable.from(1, 10));

        assertThat(find.size()).isEqualTo(10);
        assertThat(find.getTotal()).isEqualTo(22L);

        find = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenant, executionId, logEntry2.getTaskRunId(), null, 0, Pageable.from(1, 10));

        assertThat(find.size()).isEqualTo(10);
        assertThat(find.getTotal()).isEqualTo(22L);

        find = logRepository.findByExecutionIdAndTaskRunId(tenant, executionId, logEntry2.getTaskRunId(), null, Pageable.from(10, 10));

        assertThat(find.size()).isZero();
    }

    @Test
    void shouldFindByExecutionIdTestLogs() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var builder = logEntry(tenant, Level.INFO).executionId("123").executionKind(ExecutionKind.TEST).build();
        logRepository.save(builder);

        List<LogEntry> logs = logRepository.findByExecutionId(tenant, builder.getExecutionId(), null);
        assertThat(logs).hasSize(1);
    }

    @Test
    void deleteByQuery() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        LogEntry log1 = logEntry(tenant, Level.INFO).build();
        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, log1.getExecutionId(), null, null, null, null);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, "io.kestra.unittest", "flowId", null, List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1));

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, "io.kestra.unittest", "flowId", null);

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, null, null, log1.getExecutionId(), List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1));

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size()).isZero();
    }

    @Test
    void findAllAsync() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO).build());
        logRepository.save(logEntry(tenant, Level.INFO).executionKind(ExecutionKind.TEST).build()); // should be present as it's used for backup
        logRepository.save(logEntry(tenant, Level.ERROR).build());
        logRepository.save(logEntry(tenant, Level.WARN).build());

        Flux<LogEntry> find = logRepository.findAllAsync(tenant);
        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(4);
    }

    @Test
    void fetchData() throws IOException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO).build());

        var results = logRepository.fetchData(tenant,
            Logs.builder()
                .type(Logs.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Logs.Fields>builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build()
                ))
                .build(),
            ZonedDateTime.now().minusHours(3),
            ZonedDateTime.now(),
            null);

        assertThat(results).hasSize(1);
    }

    @Test
    void purge() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "execution1").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution1").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution2").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution2").build());

        var result = logRepository.purge(List.of(Execution.builder().id("execution1").build(), Execution.builder().id("execution2").build()));
        assertThat(result).isEqualTo(4);
    }
}
