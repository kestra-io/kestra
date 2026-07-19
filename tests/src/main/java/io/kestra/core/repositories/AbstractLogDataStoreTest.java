package io.kestra.core.repositories;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

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
import io.kestra.plugin.core.dashboard.data.LogsKPI;

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Flux;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
import static io.kestra.core.models.flows.FlowScope.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
public abstract class AbstractLogDataStoreTest {
    @Inject
    protected LogDataStoreInterface logRepository;

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
    void should_find_all(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        List<LogEntry> entries = logRepository.find(Pageable.UNPAGED, tenant, List.of(filter)).getContent();

        assertThat(entries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_async(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        Flux<LogEntry> find = logRepository.findAsync(tenant, List.of(filter));

        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_delete_with_filter(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "executionId").build());

        logRepository.deleteByFilters(tenant, List.of(filter));

        if (logRepository.canPurge()) {
            assertThat(logRepository.findAllAsync(tenant).collectList().block()).isEmpty();
        } else {
            // Stores that can't purge no-op: the entry is still there.
            assertThat(logRepository.findAllAsync(tenant).collectList().block()).hasSize(1);
        }
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
            QueryFilter.builder().field(Field.FLOW_ID).value(List.of("flowId", "other")).operation(Op.IN).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value(List.of("anotherFlowId")).operation(Op.NOT_IN).build(),
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
            QueryFilter.builder().field(Field.LEVEL).value(Level.DEBUG).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.LEVEL).value(Level.INFO).operation(Op.LESS_THAN_OR_EQUAL_TO).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter) {
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> logRepository.find(
                Pageable.UNPAGED,
                TestsUtils.randomTenant(this.getClass().getSimpleName()),
                List.of(filter)
            )
        );

    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.STATE).value(State.Type.RUNNING).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.LEVEL).value(Level.INFO).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.LEVEL).value(Level.INFO).operation(Op.NOT_EQUALS).build()
        );
    }

    @Test
    void shouldPersistTaskIdLongerThan150Chars() {
        // A plugin-generated taskId (e.g. Ansible "<host> | <play> : <task>") can exceed the legacy
        // VARCHAR(150) task_id column and crash-loop the indexer; the column now allows up to 256.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String longTaskId = "a".repeat(200);
        LogEntry log = logEntry(tenant, Level.INFO, "execLongTaskId").taskId(longTaskId).build();

        LogEntry saved = logRepository.save(log);

        List<LogEntry> found = logRepository.findByExecutionIdAndTaskId(tenant, saved.getExecutionId(), longTaskId, null);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getTaskId()).isEqualTo(longTaskId);
    }

    @Test
    void all() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        LogEntry.LogEntryBuilder builder = logEntry(tenant, Level.INFO);

        List<LogEntry> find = logRepository.find(Pageable.UNPAGED, tenant, null).getContent();
        assertThat(find.size()).isZero();

        LogEntry save = logRepository.save(builder.build());
        logRepository.save(builder.executionKind(ExecutionKind.TEST).build()); // non-NORMAL: excluded from the default NORMAL-only listing

        find = logRepository.find(Pageable.UNPAGED, tenant, null).getContent();
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());
        var filters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.LEVEL)
                .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                .value(Level.WARN)
                .build(),
            QueryFilter.builder()
                .field(Field.START_DATE)
                .operation(QueryFilter.Op.GREATER_THAN)
                .value(Instant.now().minus(1, ChronoUnit.HOURS))
                .build()
        );
        find = logRepository.find(Pageable.UNPAGED, "doe", filters).getContent();
        assertThat(find.size()).isZero();

        find = logRepository.find(Pageable.UNPAGED, tenant, null).getContent();
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        logRepository.find(Pageable.UNPAGED, "kestra-io/kestra", null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionId()).isEqualTo(save.getExecutionId());

        // An explicit KIND filter overrides the NORMAL default and selects the requested kind.
        find = logRepository.find(
            Pageable.UNPAGED, tenant, List.of(
                QueryFilter.builder()
                    .field(Field.KIND)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(ExecutionKind.TEST.name())
                    .build()
            )
        ).getContent();
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getExecutionKind()).isEqualTo(ExecutionKind.TEST);

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
        list = logRepository.findByExecutionIdAndTaskId(tenant, save.getExecutionId(), save.getTaskId(), null);
        if (logRepository.canPurge()) {
            assertThat(countDeleted).isEqualTo(2);
            assertThat(list.size()).isZero();
        } else {
            // A store that can't purge no-ops and returns 0, leaving the logs in place.
            assertThat(countDeleted).isZero();
            assertThat(list.size()).isEqualTo(2);
        }
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

        if (logRepository.paginationType() == PaginationType.OFFSET) {
            // Offset pagination: exact total + random page access.
            Page<LogEntry> find = logRepository.findByExecutionId(tenant, executionId, null, Pageable.from(1, 50));

            assertThat(find.getNumberOfElements()).isEqualTo(50);
            assertThat(find.getTotalSize()).isEqualTo(102L);

            find = logRepository.findByExecutionId(tenant, executionId, null, Pageable.from(3, 50));

            assertThat(find.getNumberOfElements()).isEqualTo(2);
            assertThat(find.getTotalSize()).isEqualTo(102L);

            find = logRepository.findByExecutionIdAndTaskId(tenant, executionId, logEntry2.getTaskId(), null, Pageable.from(1, 50));

            assertThat(find.getNumberOfElements()).isEqualTo(22);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logRepository.findByExecutionIdAndTaskRunId(tenant, executionId, logEntry2.getTaskRunId(), null, Pageable.from(1, 10));

            assertThat(find.getNumberOfElements()).isEqualTo(10);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenant, executionId, logEntry2.getTaskRunId(), null, 0, Pageable.from(1, 10));

            assertThat(find.getNumberOfElements()).isEqualTo(10);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logRepository.findByExecutionIdAndTaskRunId(tenant, executionId, logEntry2.getTaskRunId(), null, Pageable.from(10, 10));

            assertThat(find.getNumberOfElements()).isZero();
        } else {
            // Cursor pagination: no total, forward-only. Walk the opaque cursor to the end, accumulating the row
            // count — this proves the cursor advances and terminates, without relying on a total or random page
            // access (and is robust to identical rows, unlike an element-disjointness check).
            List<QueryFilter> filters = List.of(
                QueryFilter.builder().field(QueryFilter.Field.EXECUTION_ID).operation(QueryFilter.Op.EQUALS).value(executionId).build()
            );

            int pageSize = 20;
            int collected = 0;
            Pageable.Cursor cursor = null;
            for (int guard = 0; guard < 100; guard++) {
                Pageable request = cursor == null
                    ? Pageable.from(1, pageSize)
                    : Pageable.afterCursor(cursor, 1, pageSize, Sort.UNSORTED);

                Page<LogEntry> page = logRepository.find(request, tenant, filters);
                assertThat(page).isInstanceOf(CursoredPage.class);
                assertThat(page.hasTotalSize()).isFalse(); // cursor stores expose no total

                if (page.getContent().isEmpty()) {
                    break;
                }
                collected += page.getNumberOfElements();
                List<Pageable.Cursor> cursors = ((CursoredPage<LogEntry>) page).getCursors();
                cursor = cursors.get(cursors.size() - 1);
            }

            assertThat(collected).isEqualTo(102);
        }
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

        if (!logRepository.canPurge()) {
            // A store that can't purge no-ops: deleteByQuery leaves the entry in place.
            logRepository.deleteByQuery(tenant, log1.getExecutionId(), null, null, null, null);
            assertThat(logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50)).getNumberOfElements()).isEqualTo(1);
            return;
        }

        logRepository.deleteByQuery(tenant, log1.getExecutionId(), null, null, null, null);

        Page<LogEntry> find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.getNumberOfElements()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, "io.kestra.unittest", "flowId", null, List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1), true, true, null);

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.getNumberOfElements()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, "io.kestra.unittest", "flowId", null);

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.getNumberOfElements()).isZero();

        logRepository.save(log1);

        logRepository.deleteByQuery(tenant, null, null, log1.getExecutionId(), List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1), true, true, null);

        find = logRepository.findByExecutionId(tenant, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.getNumberOfElements()).isZero();
    }

    @Test
    void deleteByQueryWithBatchSize() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        for (int i = 0; i < 5; i++) {
            logRepository.save(logEntry(tenant, Level.INFO, executionId).build());
        }

        int deleted = logRepository.deleteByQuery(tenant, "io.kestra.unittest", "flowId", null, null, null, ZonedDateTime.now().plusMinutes(1), true, true, 2);
        var remaining = logRepository.findByExecutionId(tenant, executionId, null, Pageable.from(1, 50));

        if (logRepository.canPurge()) {
            assertThat(deleted).isEqualTo(5);
            assertThat(remaining.getNumberOfElements()).isZero();
        } else {
            // No-op store: nothing deleted, all rows remain.
            assertThat(deleted).isZero();
            assertThat(remaining.getNumberOfElements()).isEqualTo(5);
        }
    }

    @Test
    void deleteByQueryWithNamespacePrefix() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        LogEntry parent = logEntry(tenant, Level.INFO, "exec-parent").namespace("io.kestra.purge").build();
        LogEntry child = logEntry(tenant, Level.INFO, "exec-child").namespace("io.kestra.purge.child").build();
        LogEntry sibling = logEntry(tenant, Level.INFO, "exec-sibling").namespace("io.kestra.purgeother").build();
        logRepository.save(parent);
        logRepository.save(child);
        logRepository.save(sibling);

        // Without a flowId, the namespace is a prefix: the namespace itself and its descendants are
        // purged, while sibling namespaces sharing a textual prefix (io.kestra.purgeother) are kept.
        int deleted = logRepository.deleteByQuery(tenant, "io.kestra.purge", null, null, null, null, ZonedDateTime.now().plusMinutes(1), true, true, null);

        if (logRepository.canPurge()) {
            assertThat(deleted).isEqualTo(2);
            assertThat(logRepository.findByExecutionId(tenant, parent.getExecutionId(), null)).isEmpty();
            assertThat(logRepository.findByExecutionId(tenant, child.getExecutionId(), null)).isEmpty();
            assertThat(logRepository.findByExecutionId(tenant, sibling.getExecutionId(), null)).hasSize(1);
        } else {
            // No-op store: nothing deleted, all three remain.
            assertThat(deleted).isZero();
            assertThat(logRepository.findByExecutionId(tenant, parent.getExecutionId(), null)).hasSize(1);
            assertThat(logRepository.findByExecutionId(tenant, child.getExecutionId(), null)).hasSize(1);
            assertThat(logRepository.findByExecutionId(tenant, sibling.getExecutionId(), null)).hasSize(1);
        }
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

        // test log should not be included in the results
        logRepository.save(logEntry(tenant, Level.INFO).executionKind(ExecutionKind.TEST).build());

        var results = logRepository.fetchData(
            tenant,
            Logs.builder()
                .type(Logs.class.getName())
                .columns(
                    Map.of(
                        "count", ColumnDescriptor.<Logs.Fields> builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build()
                    )
                )
                .build(),
            ZonedDateTime.now().minusHours(3),
            ZonedDateTime.now(),
            null
        );

        if (logRepository.canAggregate()) {
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().get("count")).isIn(1, 1L); // JDBC return an int but ES a long
        } else {
            // A store that can't aggregate yields no rows (dashboards render "No data").
            assertThat(results).isEmpty();
        }
    }

    @Test
    void fetchValue() throws IOException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO).build());

        // test log should not be included in the results
        logRepository.save(logEntry(tenant, Level.INFO).executionKind(ExecutionKind.TEST).build());

        var results = logRepository.fetchValue(
            tenant,
            LogsKPI.builder()
                .type(LogsKPI.class.getName())
                .columns(ColumnDescriptor.<Logs.Fields> builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build())
                .build(),
            ZonedDateTime.now().minusHours(3),
            ZonedDateTime.now(),
            false
        );

        // A store that can't aggregate yields a zero KPI (so dashboards render "No data").
        assertThat(results).isEqualTo(logRepository.canAggregate() ? 1.0 : 0.0);
    }

    @Test
    void purge() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        logRepository.save(logEntry(tenant, Level.INFO, "execution1").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution1").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution2").build());
        logRepository.save(logEntry(tenant, Level.INFO, "execution2").build());

        var result = logRepository.purge(List.of(Execution.builder().id("execution1").build(), Execution.builder().id("execution2").build()));

        if (logRepository.canPurge()) {
            assertThat(result).isEqualTo(4);
        } else {
            // No-op store: returns 0 and leaves the logs in place.
            assertThat(result).isZero();
            assertThat(logRepository.findByExecutionId(tenant, "execution1", null)).hasSize(2);
        }
    }

    private static final LogEntry traceLog = logEntry(null, Level.TRACE, "exec-trace").build();
    private static final LogEntry debugLog = logEntry(null, Level.DEBUG, "exec-debug").build();
    private static final LogEntry infoLog = logEntry(null, Level.INFO, "exec-info").build();
    private static final LogEntry warnLog = logEntry(null, Level.WARN, "exec-warn").build();
    private static final LogEntry errorLog = logEntry(null, Level.ERROR, "exec-error").build();
    private static final List<LogEntry> allLevels = List.of(traceLog, debugLog, infoLog, warnLog, errorLog);

    private static final LogEntry loadDataLog = logEntry(null, Level.INFO, "exec-load-data")
        .taskId("load-data").taskRunId("tr-load-data").attemptNumber(0).build();
    private static final LogEntry transformLog = logEntry(null, Level.INFO, "exec-transform")
        .taskId("transform").taskRunId("tr-transform").attemptNumber(1).build();
    private static final LogEntry sinkLog = logEntry(null, Level.INFO, "exec-sink")
        .taskId("sink").taskRunId("tr-sink").attemptNumber(2).build();
    private static final List<LogEntry> taskVariedLogs = List.of(loadDataLog, transformLog, sinkLog);

    private static final LogEntry alphaLog = logEntry(null, Level.INFO, "exec-alpha")
        .namespace("io.kestra.alpha")
        .flowId("alpha-flow").triggerId("alpha-trigger")
        .taskId("alpha-task").taskRunId("alpha-tr")
        .message("alpha message").build();
    private static final LogEntry betaLog = logEntry(null, Level.INFO, "exec-beta")
        .namespace("io.kestra.beta")
        .flowId("beta-flow").triggerId("beta-trigger")
        .taskId("beta-task").taskRunId("beta-tr")
        .message("beta message").build();
    private static final LogEntry gammaLog = logEntry(null, Level.INFO, "exec-gamma")
        .namespace("com.example.gamma")
        .flowId("gamma-flow").triggerId("gamma-trigger")
        .taskId("gamma-task").taskRunId("gamma-tr")
        .message("gamma message").build();
    private static final List<LogEntry> distinctLogs = List.of(alphaLog, betaLog, gammaLog);

    private static final LogEntry userScopeLog = logEntry(null, Level.INFO, "exec-user-scope")
        .namespace("io.kestra.user").build();
    private static final LogEntry systemScopeLog = logEntry(null, Level.INFO, "exec-system-scope")
        .namespace("system").build();
    private static final List<LogEntry> scopeLogs = List.of(userScopeLog, systemScopeLog);

    private static final Instant T_PAST = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant T_NOW = Instant.parse("2020-06-01T00:00:00Z");
    private static final Instant T_FUTURE = Instant.parse("2020-12-31T00:00:00Z");
    private static final LogEntry pastLog = logEntry(null, Level.INFO, "exec-past").timestamp(T_PAST).build();
    private static final LogEntry nowLog = logEntry(null, Level.INFO, "exec-now").timestamp(T_NOW).build();
    private static final LogEntry futureLog = logEntry(null, Level.INFO, "exec-future").timestamp(T_FUTURE).build();
    private static final List<LogEntry> timeLogs = List.of(pastLog, nowLog, futureLog);

    private static final LogEntry normalKindLog = logEntry(null, Level.INFO, "exec-normal-kind")
        .executionKind(ExecutionKind.NORMAL).build();
    private static final LogEntry playgroundKindLog = logEntry(null, Level.INFO, "exec-playground-kind")
        .executionKind(ExecutionKind.PLAYGROUND).build();
    private static final LogEntry loopKindLog = logEntry(null, Level.INFO, "exec-loop-kind")
        .executionKind(ExecutionKind.LOOP).build();
    private static final List<LogEntry> kindLogs = List.of(normalKindLog, playgroundKindLog, loopKindLog);

    public static final List<FiltersTestCase> filtersTestCases = List.of(
        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(List.of(infoLog, warnLog, errorLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.INFO).operation(Op.GREATER_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(List.of(traceLog, debugLog, infoLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.INFO).operation(Op.LESS_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(allLevels)
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.TRACE).operation(Op.GREATER_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(allLevels)
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.ERROR).operation(Op.LESS_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(List.of(errorLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.ERROR).operation(Op.GREATER_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(allLevels)
            .expectedLogs(List.of(traceLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.LEVEL).value(Level.TRACE).operation(Op.LESS_THAN_OR_EQUAL_TO)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(loadDataLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.TASK_ID).value("load-data").operation(Op.EQUALS)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(transformLog, sinkLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.TASK_ID).value("load-data").operation(Op.NOT_EQUALS)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(loadDataLog, transformLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.TASK_ID).value(List.of("load-data", "transform")).operation(Op.IN)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(transformLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.TASK_RUN_ID).value("tr-transform").operation(Op.EQUALS)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(loadDataLog, sinkLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.TASK_RUN_ID).value(List.of("tr-load-data", "tr-sink")).operation(Op.IN)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(transformLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.ATTEMPT_NUMBER).value(1).operation(Op.EQUALS)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(loadDataLog, sinkLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.ATTEMPT_NUMBER).value(1).operation(Op.NOT_EQUALS)
                    .build()
            )
            .build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(loadDataLog, transformLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.ATTEMPT_NUMBER).value(List.of(0, 1)).operation(Op.IN)
                    .build()
            ).build(),

        FiltersTestCase.builder()
            .logs(taskVariedLogs)
            .expectedLogs(List.of(transformLog))
            .queryFilter(
                QueryFilter.builder()
                    .field(Field.ATTEMPT_NUMBER).value(List.of(0, 2)).operation(Op.NOT_IN)
                    .build()
            ).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_ID).value("alpha").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_ID).value("alpha").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_ID).value("alpha-task").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_ID).value(List.of("alpha-task", "beta-task")).operation(Op.NOT_IN).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_RUN_ID).value("alpha-tr").operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_RUN_ID).value("beta").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_RUN_ID).value("alpha").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_RUN_ID).value("gamma-tr").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TASK_RUN_ID).value(List.of("alpha-tr", "beta-tr")).operation(Op.NOT_IN).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.QUERY).value("alpha message").operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.QUERY).value("alpha message").operation(Op.NOT_EQUALS).build()).build(),

        FiltersTestCase.builder()
            .logs(scopeLogs).expectedLogs(List.of(userScopeLog))
            .queryFilter(QueryFilter.builder().field(Field.SCOPE).value(List.of(io.kestra.core.models.flows.FlowScope.USER)).operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(scopeLogs).expectedLogs(List.of(systemScopeLog))
            .queryFilter(QueryFilter.builder().field(Field.SCOPE).value(List.of(io.kestra.core.models.flows.FlowScope.USER)).operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(scopeLogs).expectedLogs(List.of(userScopeLog))
            .queryFilter(QueryFilter.builder().field(Field.SCOPE).value(List.of(io.kestra.core.models.flows.FlowScope.USER)).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(scopeLogs).expectedLogs(List.of(systemScopeLog))
            .queryFilter(QueryFilter.builder().field(Field.SCOPE).value(List.of(io.kestra.core.models.flows.FlowScope.USER)).operation(Op.NOT_IN).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.alpha").operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.alpha").operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("kestra").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("gamma").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("io\\.kestra.*").operation(Op.REGEX).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value(List.of("io.kestra.alpha", "io.kestra.beta")).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value(List.of("io.kestra.alpha", "io.kestra.beta")).operation(Op.NOT_IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra").operation(Op.PREFIX).build()).build(),

        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(nowLog, futureLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.GREATER_THAN_OR_EQUAL_TO).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(futureLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.GREATER_THAN).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog, nowLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.LESS_THAN_OR_EQUAL_TO).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.LESS_THAN).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(nowLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog, futureLog))
            .queryFilter(QueryFilter.builder().field(Field.START_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.NOT_EQUALS).build()).build(),

        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(nowLog, futureLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.GREATER_THAN_OR_EQUAL_TO).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(futureLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.GREATER_THAN).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog, nowLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.LESS_THAN_OR_EQUAL_TO).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.LESS_THAN).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(nowLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(timeLogs).expectedLogs(List.of(pastLog, futureLog))
            .queryFilter(QueryFilter.builder().field(Field.END_DATE).value(T_NOW.atZone(java.time.ZoneOffset.UTC)).operation(Op.NOT_EQUALS).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha-flow").operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha-flow").operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(distinctLogs)
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("-flow").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha-.*").operation(Op.REGEX).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value(List.of("alpha-flow", "beta-flow")).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value(List.of("alpha-flow", "beta-flow")).operation(Op.NOT_IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.FLOW_ID).value("alpha-flow").operation(Op.PREFIX).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value("alpha-trigger").operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value("alpha-trigger").operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value("alpha").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value("alpha").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(distinctLogs)
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value("-trigger").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value(List.of("alpha-trigger", "beta-trigger")).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.TRIGGER_ID).value(List.of("alpha-trigger", "beta-trigger")).operation(Op.NOT_IN).build()).build(),

        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value("exec-alpha").operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(betaLog, gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value("exec-alpha").operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value("alpha").operation(Op.CONTAINS).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(distinctLogs)
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value("exec-").operation(Op.STARTS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value("alpha").operation(Op.ENDS_WITH).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(alphaLog, betaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value(List.of("exec-alpha", "exec-beta")).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(distinctLogs).expectedLogs(List.of(gammaLog))
            .queryFilter(QueryFilter.builder().field(Field.EXECUTION_ID).value(List.of("exec-alpha", "exec-beta")).operation(Op.NOT_IN).build()).build(),

        FiltersTestCase.builder()
            .logs(kindLogs).expectedLogs(List.of(playgroundKindLog))
            .queryFilter(QueryFilter.builder().field(Field.KIND).value(ExecutionKind.PLAYGROUND.name()).operation(Op.EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(kindLogs).expectedLogs(List.of(normalKindLog, loopKindLog))
            .queryFilter(QueryFilter.builder().field(Field.KIND).value(ExecutionKind.PLAYGROUND.name()).operation(Op.NOT_EQUALS).build()).build(),
        FiltersTestCase.builder()
            .logs(kindLogs).expectedLogs(List.of(playgroundKindLog, loopKindLog))
            .queryFilter(QueryFilter.builder().field(Field.KIND).value(List.of(ExecutionKind.PLAYGROUND.name(), ExecutionKind.LOOP.name())).operation(Op.IN).build()).build(),
        FiltersTestCase.builder()
            .logs(kindLogs).expectedLogs(List.of(normalKindLog))
            .queryFilter(QueryFilter.builder().field(Field.KIND).value(List.of(ExecutionKind.PLAYGROUND.name(), ExecutionKind.LOOP.name())).operation(Op.NOT_IN).build()).build()
    );

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void findWithFilters(FiltersTestCase testCase) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        testCase.logs().forEach(log -> logRepository.save(log.toBuilder().tenantId(tenant).build()));

        List<LogEntry> results = logRepository.find(
            Pageable.UNPAGED, tenant, List.of(testCase.queryFilter())
        ).getContent();

        assertThat(results)
            .extracting(LogEntry::getExecutionId)
            .containsExactlyInAnyOrderElementsOf(
                testCase.expectedLogs().stream().map(LogEntry::getExecutionId).toList()
            );
    }

    @Test
    void findDefaultsToNormalKindWhenNoKindFilter() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        kindLogs.forEach(log -> logRepository.save(log.toBuilder().tenantId(tenant).build()));

        List<LogEntry> results = logRepository.find(Pageable.UNPAGED, tenant, null).getContent();

        assertThat(results)
            .extracting(LogEntry::getExecutionId)
            .containsExactly(normalKindLog.getExecutionId());
    }

    @Builder
    public record FiltersTestCase(
        List<LogEntry> logs,
        List<LogEntry> expectedLogs,
        QueryFilter queryFilter) {
    }

    @Test
    void shouldFindByExecutionIdWithMinLevelFilter() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();

        logRepository.save(logEntry(tenant, Level.DEBUG, executionId).build());
        logRepository.save(logEntry(tenant, Level.WARN, executionId).build());
        logRepository.save(logEntry(tenant, Level.ERROR, executionId).build());

        List<LogEntry> results = logRepository.findByExecutionId(tenant, executionId, Level.WARN);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(LogEntry::getLevel).containsExactlyInAnyOrder(Level.WARN, Level.ERROR);
    }
}
