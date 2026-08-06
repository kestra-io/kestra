package io.kestra.core.repositories;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Logical;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowScope;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The contract every {@link LogDataStoreInterface} backend must satisfy — JDBC, Elasticsearch, and external stores
 * such as GCP Cloud Logging. It doubles as the executable spec for plugin authors, so it exercises <b>every</b>
 * interface method with <b>real parameter values</b> (not just {@code null}) and asserts <b>round-trip fidelity and
 * correct filtering</b>, not just row counts.
 *
 * <h2>Load-once design</h2>
 * Read fixtures are written once in {@link #seed()} — each group under its own random tenant — followed by a single
 * {@link #awaitIndexing(BooleanSupplier)}. Read tests then assert against that data with no further writes, which keeps
 * the suite fast and cheap on eventually-consistent / quota-limited backends (a write→read per test would need a wait
 * per test). Read-your-write backends (JDBC/ES) leave {@code awaitIndexing} a no-op, so they never wait. Random tenants
 * make the suite safe to re-run without cleaning the store (queries are tenant-scoped). Write/mutate tests provision
 * their own isolated data and call {@code awaitIndexing} themselves, so they never disturb the shared read fixtures.
 */
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractLogDataStoreTest {

    @Inject
    protected LogDataStoreInterface logDataStore;

    /**
     * Blocks until just-written data is queryable. No-op for read-your-write backends (JDBC/ES); eventually
     * consistent backends (e.g. GCP Cloud Logging) override it to poll {@code ready} until true (or a timeout).
     */
    protected void awaitIndexing(BooleanSupplier ready) {
        // read-your-write: nothing to wait for
    }

    private void awaitVisible(String tenantId, String executionId) {
        awaitIndexing(() -> !logDataStore.findByExecutionId(tenantId, executionId, null).isEmpty());
    }

    // ------------------------------------------------------------------ fixtures (written once)

    /** One random tenant per fixture group so groups never contaminate each other's assertions. */
    protected String levelsTenant;
    protected String tasksTenant;
    protected String distinctTenant;
    protected String timeTenant;
    protected String kindTenant;
    protected String scopeTenant;
    protected String familyTenant;
    protected String pageTenant;

    protected enum Group {
        LEVELS,
        TASKS,
        DISTINCT,
        TIME,
        KIND,
        SCOPE
    }

    protected String tenantOf(Group group) {
        return switch (group) {
            case LEVELS -> levelsTenant;
            case TASKS -> tasksTenant;
            case DISTINCT -> distinctTenant;
            case TIME -> timeTenant;
            case KIND -> kindTenant;
            case SCOPE -> scopeTenant;
        };
    }

    protected static LogEntry.LogEntryBuilder log(Level level, String executionId) {
        return LogEntry.builder()
            .namespace("io.kestra.unittest")
            .flowId("flowId")
            .taskId("taskId")
            .executionId(executionId)
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .triggerId("triggerId")
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .message("john doe");
    }

    private static final List<LogEntry> LEVELS_LOGS = List.of(
        log(Level.TRACE, "exec-trace").build(),
        log(Level.DEBUG, "exec-debug").build(),
        log(Level.INFO, "exec-info").build(),
        log(Level.WARN, "exec-warn").build(),
        log(Level.ERROR, "exec-error").build()
    );

    private static final List<LogEntry> TASK_LOGS = List.of(
        log(Level.INFO, "exec-load-data").taskId("load-data").taskRunId("tr-load-data").attemptNumber(0).build(),
        log(Level.INFO, "exec-transform").taskId("transform").taskRunId("tr-transform").attemptNumber(1).build(),
        log(Level.INFO, "exec-sink").taskId("sink").taskRunId("tr-sink").attemptNumber(2).build()
    );

    // The three levels are DISTINCT on purpose: fetchData/fetchValue aggregate COUNT over the LEVEL field, and
    // backends legitimately differ on "COUNT of a field" — JDBC counts rows, Elasticsearch counts distinct values
    // (cardinality). Distinct levels make COUNT(level) == 3 under both semantics, keeping the aggregation assertion
    // backend-agnostic. Do not collapse these back to a single level. (No DISTINCT filter case filters on level.)
    private static final List<LogEntry> DISTINCT_LOGS = List.of(
        log(Level.INFO, "exec-alpha").namespace("io.kestra.alpha").flowId("alpha-flow").triggerId("alpha-trigger").taskId("alpha-task").taskRunId("alpha-tr").message("alpha message").build(),
        log(Level.WARN, "exec-beta").namespace("io.kestra.beta").flowId("beta-flow").triggerId("beta-trigger").taskId("beta-task").taskRunId("beta-tr").message("beta message").build(),
        log(Level.ERROR, "exec-gamma").namespace("com.example.gamma").flowId("gamma-flow").triggerId("gamma-trigger").taskId("gamma-task").taskRunId("gamma-tr").message("gamma message")
            .build()
    );

    // Timestamps are relative to now, never fixed historical dates: retention-limited backends (Cloud Logging drops
    // entries older than ~30 days, so they never become queryable) would make fixed-past fixtures un-runnable. The
    // three points stay ordered (past < now < future) and all sit comfortably within retention. The TIME group has
    // its own tenant, so only their relative order matters to the assertions — the absolute anchor is irrelevant.
    private static final Instant T_NOW = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant T_PAST = T_NOW.minus(4, ChronoUnit.DAYS);
    private static final Instant T_FUTURE = T_NOW.plus(4, ChronoUnit.DAYS);
    private static final ZonedDateTime T_NOW_ZDT = T_NOW.atZone(ZoneOffset.UTC);
    private static final List<LogEntry> TIME_LOGS = List.of(
        log(Level.INFO, "exec-past").timestamp(T_PAST).build(),
        log(Level.INFO, "exec-now").timestamp(T_NOW).build(),
        log(Level.INFO, "exec-future").timestamp(T_FUTURE).build()
    );

    private static final List<LogEntry> KIND_LOGS = List.of(
        log(Level.INFO, "exec-normal-kind").executionKind(ExecutionKind.NORMAL).build(),
        log(Level.INFO, "exec-playground-kind").executionKind(ExecutionKind.PLAYGROUND).build(),
        log(Level.INFO, "exec-loop-kind").executionKind(ExecutionKind.LOOP).build()
    );

    private static final List<LogEntry> SCOPE_LOGS = List.of(
        log(Level.INFO, "exec-user-scope").namespace("io.kestra.user").build(),
        log(Level.INFO, "exec-system-scope").namespace("system").build()
    );

    // One execution, one task, one taskrun, three attempts with increasing levels — drives the whole
    // findByExecutionId* family with real min-level / attempt / namespace-flow parameters.
    static final String FAMILY_EXEC = "exec-family";
    static final String FAMILY_NS = "io.kestra.family";
    static final String FAMILY_FLOW = "family-flow";
    static final String FAMILY_TASK = "family-task";
    static final String FAMILY_TR = "family-tr";
    private static final List<LogEntry> FAMILY_LOGS = List.of(
        log(Level.INFO, FAMILY_EXEC).namespace(FAMILY_NS).flowId(FAMILY_FLOW).taskId(FAMILY_TASK).taskRunId(FAMILY_TR).attemptNumber(0).build(),
        log(Level.WARN, FAMILY_EXEC).namespace(FAMILY_NS).flowId(FAMILY_FLOW).taskId(FAMILY_TASK).taskRunId(FAMILY_TR).attemptNumber(1).build(),
        log(Level.ERROR, FAMILY_EXEC).namespace(FAMILY_NS).flowId(FAMILY_FLOW).taskId(FAMILY_TASK).taskRunId(FAMILY_TR).attemptNumber(2).build()
    );

    // Pagination fixture: 102 entries for one execution (80 on taskId, 22 on taskId2/taskRunId2).
    static final String PAGE_EXEC = "exec-page";

    @BeforeAll
    void seed() {
        levelsTenant = randomTenant();
        tasksTenant = randomTenant();
        distinctTenant = randomTenant();
        timeTenant = randomTenant();
        kindTenant = randomTenant();
        scopeTenant = randomTenant();
        familyTenant = randomTenant();
        pageTenant = randomTenant();

        // One bulk write per group (saveBatch) — far fewer requests than a save() per entry on remote backends.
        logDataStore.saveBatch(withTenant(levelsTenant, LEVELS_LOGS));
        logDataStore.saveBatch(withTenant(tasksTenant, TASK_LOGS));
        logDataStore.saveBatch(withTenant(distinctTenant, DISTINCT_LOGS));
        logDataStore.saveBatch(withTenant(timeTenant, TIME_LOGS));
        logDataStore.saveBatch(withTenant(kindTenant, KIND_LOGS));
        logDataStore.saveBatch(withTenant(scopeTenant, SCOPE_LOGS));
        logDataStore.saveBatch(withTenant(familyTenant, FAMILY_LOGS));

        List<LogEntry> pageLogs = new ArrayList<>(102);
        for (int i = 0; i < 80; i++) {
            pageLogs.add(log(Level.INFO, PAGE_EXEC).tenantId(pageTenant).build());
        }
        for (int i = 0; i < 22; i++) {
            pageLogs.add(log(Level.INFO, PAGE_EXEC).tenantId(pageTenant).taskId("taskId2").taskRunId("taskRunId2").build());
        }
        logDataStore.saveBatch(pageLogs);

        // One wait for the whole suite: block until the LAST write is queryable — everything else was written
        // microseconds earlier, so it is indexed by then too. A single probe read per tick keeps read-quota use low.
        awaitIndexing(() -> !logDataStore.findByExecutionId(pageTenant, PAGE_EXEC, null).isEmpty());
    }

    private static List<LogEntry> withTenant(String tenantId, List<LogEntry> logs) {
        return logs.stream().map(l -> l.toBuilder().tenantId(tenantId).build()).toList();
    }

    private static String randomTenant() {
        return TestsUtils.randomTenant();
    }

    // ------------------------------------------------------------------ writes

    @Test
    void save_preservesEveryField() {
        // The core contract for plugin authors: a saved entry reads back identical on every field.
        String tenant = randomTenant();
        LogEntry saved = LogEntry.builder()
            .tenantId(tenant)
            .namespace("io.kestra.roundtrip")
            .flowId("rt-flow")
            .taskId("rt-task")
            .executionId("rt-exec")
            .taskRunId("rt-tr")
            .attemptNumber(2)
            .triggerId("rt-trigger")
            // Current-second timestamp (fastest to become queryable on eventually-consistent backends) plus a
            // sub-second component so millisecond round-trip fidelity is still asserted.
            .timestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(123))
            .level(Level.WARN)
            .thread("rt-thread")
            .message("round trip message")
            .executionKind(ExecutionKind.NORMAL)
            .progress("rt-progress")
            .build();

        logDataStore.save(saved);
        awaitVisible(tenant, "rt-exec");

        List<LogEntry> found = logDataStore.findByExecutionId(tenant, "rt-exec", null);
        assertThat(found).hasSize(1);
        LogEntry read = found.getFirst();
        assertThat(read.getTenantId()).isEqualTo(tenant);
        assertThat(read.getNamespace()).isEqualTo("io.kestra.roundtrip");
        assertThat(read.getFlowId()).isEqualTo("rt-flow");
        assertThat(read.getTaskId()).isEqualTo("rt-task");
        assertThat(read.getExecutionId()).isEqualTo("rt-exec");
        assertThat(read.getTaskRunId()).isEqualTo("rt-tr");
        assertThat(read.getAttemptNumber()).isEqualTo(2);
        assertThat(read.getTriggerId()).isEqualTo("rt-trigger");
        assertThat(read.getLevel()).isEqualTo(Level.WARN);
        assertThat(read.getThread()).isEqualTo("rt-thread");
        assertThat(read.getMessage()).isEqualTo("round trip message");
        assertThat(read.getExecutionKind()).isEqualTo(ExecutionKind.NORMAL);
        assertThat(read.getProgress()).isEqualTo("rt-progress");
        // Precision varies by backend (nanos on some, millis on others); millisecond fidelity is the contract.
        assertThat(read.getTimestamp().truncatedTo(ChronoUnit.MILLIS))
            .isEqualTo(saved.getTimestamp().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void saveBatch_returnsCountAndPersistsAll() {
        String tenant = randomTenant();
        List<LogEntry> batch = List.of(
            log(Level.INFO, "exec-batch").tenantId(tenant).build(),
            log(Level.WARN, "exec-batch").tenantId(tenant).build(),
            log(Level.ERROR, "exec-batch").tenantId(tenant).build()
        );

        assertThat(logDataStore.saveBatch(batch)).isEqualTo(3);
        awaitVisible(tenant, "exec-batch");

        assertThat(logDataStore.findByExecutionId(tenant, "exec-batch", null)).hasSize(3);
    }

    // ------------------------------------------------------------------ find() — exhaustive filter matrix

    @Builder
    public record FilterCase(Group group, QueryFilter filter, List<String> expected) {
    }

    private static FilterCase leaf(Group group, Field field, Op op, Object value, String... expected) {
        return new FilterCase(group, QueryFilter.builder().field(field).operation(op).value(value).build(), List.of(expected));
    }

    private static QueryFilter and(QueryFilter... children) {
        return QueryFilter.builder().logical(Logical.AND).children(List.of(children)).build();
    }

    private static QueryFilter or(QueryFilter... children) {
        return QueryFilter.builder().logical(Logical.OR).children(List.of(children)).build();
    }

    private static QueryFilter cond(Field field, Op op, Object value) {
        return QueryFilter.builder().field(field).operation(op).value(value).build();
    }

    public static final List<FilterCase> filterCases = List.of(
        // LEVEL (ordinal range on LEVELS — one entry per level, distinct executions)
        leaf(Group.LEVELS, Field.LEVEL, Op.GREATER_THAN_OR_EQUAL_TO, Level.INFO, "exec-info", "exec-warn", "exec-error"),
        leaf(Group.LEVELS, Field.LEVEL, Op.LESS_THAN_OR_EQUAL_TO, Level.INFO, "exec-trace", "exec-debug", "exec-info"),
        leaf(Group.LEVELS, Field.LEVEL, Op.GREATER_THAN_OR_EQUAL_TO, Level.TRACE, "exec-trace", "exec-debug", "exec-info", "exec-warn", "exec-error"),
        leaf(Group.LEVELS, Field.LEVEL, Op.LESS_THAN_OR_EQUAL_TO, Level.ERROR, "exec-trace", "exec-debug", "exec-info", "exec-warn", "exec-error"),
        leaf(Group.LEVELS, Field.LEVEL, Op.GREATER_THAN_OR_EQUAL_TO, Level.ERROR, "exec-error"),
        leaf(Group.LEVELS, Field.LEVEL, Op.LESS_THAN_OR_EQUAL_TO, Level.TRACE, "exec-trace"),
        leaf(Group.LEVELS, Field.LEVEL, Op.IN, List.of(Level.WARN, Level.ERROR), "exec-warn", "exec-error"),
        leaf(Group.LEVELS, Field.LEVEL, Op.NOT_IN, List.of(Level.WARN, Level.ERROR), "exec-trace", "exec-debug", "exec-info"),

        // TASK_ID / TASK_RUN_ID / ATTEMPT_NUMBER (TASKS)
        leaf(Group.TASKS, Field.TASK_ID, Op.EQUALS, "load-data", "exec-load-data"),
        leaf(Group.TASKS, Field.TASK_ID, Op.NOT_EQUALS, "load-data", "exec-transform", "exec-sink"),
        leaf(Group.TASKS, Field.TASK_ID, Op.IN, List.of("load-data", "transform"), "exec-load-data", "exec-transform"),
        leaf(Group.TASKS, Field.TASK_RUN_ID, Op.EQUALS, "tr-transform", "exec-transform"),
        leaf(Group.TASKS, Field.TASK_RUN_ID, Op.IN, List.of("tr-load-data", "tr-sink"), "exec-load-data", "exec-sink"),
        leaf(Group.TASKS, Field.ATTEMPT_NUMBER, Op.EQUALS, 1, "exec-transform"),
        leaf(Group.TASKS, Field.ATTEMPT_NUMBER, Op.NOT_EQUALS, 1, "exec-load-data", "exec-sink"),
        leaf(Group.TASKS, Field.ATTEMPT_NUMBER, Op.IN, List.of(0, 1), "exec-load-data", "exec-transform"),
        leaf(Group.TASKS, Field.ATTEMPT_NUMBER, Op.NOT_IN, List.of(0, 2), "exec-transform"),

        // TASK_ID / TASK_RUN_ID string ops (DISTINCT)
        leaf(Group.DISTINCT, Field.TASK_ID, Op.CONTAINS, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TASK_ID, Op.STARTS_WITH, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TASK_ID, Op.ENDS_WITH, "alpha-task", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TASK_ID, Op.NOT_IN, List.of("alpha-task", "beta-task"), "exec-gamma"),
        leaf(Group.DISTINCT, Field.TASK_RUN_ID, Op.NOT_EQUALS, "alpha-tr", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.TASK_RUN_ID, Op.CONTAINS, "beta", "exec-beta"),
        leaf(Group.DISTINCT, Field.TASK_RUN_ID, Op.STARTS_WITH, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TASK_RUN_ID, Op.ENDS_WITH, "gamma-tr", "exec-gamma"),
        leaf(Group.DISTINCT, Field.TASK_RUN_ID, Op.NOT_IN, List.of("alpha-tr", "beta-tr"), "exec-gamma"),

        // QUERY free-text (DISTINCT)
        leaf(Group.DISTINCT, Field.QUERY, Op.EQUALS, "alpha message", "exec-alpha"),
        leaf(Group.DISTINCT, Field.QUERY, Op.NOT_EQUALS, "alpha message", "exec-beta", "exec-gamma"),

        // SCOPE (SCOPE)
        leaf(Group.SCOPE, Field.SCOPE, Op.EQUALS, List.of(FlowScope.USER), "exec-user-scope"),
        leaf(Group.SCOPE, Field.SCOPE, Op.NOT_EQUALS, List.of(FlowScope.USER), "exec-system-scope"),
        leaf(Group.SCOPE, Field.SCOPE, Op.IN, List.of(FlowScope.USER), "exec-user-scope"),
        leaf(Group.SCOPE, Field.SCOPE, Op.NOT_IN, List.of(FlowScope.USER), "exec-system-scope"),

        // NAMESPACE (DISTINCT)
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.EQUALS, "io.kestra.alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.NOT_EQUALS, "io.kestra.alpha", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.CONTAINS, "kestra", "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.STARTS_WITH, "io.kestra", "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.ENDS_WITH, "gamma", "exec-gamma"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.REGEX, "io\\.kestra.*", "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.IN, List.of("io.kestra.alpha", "io.kestra.beta"), "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.NOT_IN, List.of("io.kestra.alpha", "io.kestra.beta"), "exec-gamma"),
        leaf(Group.DISTINCT, Field.NAMESPACE, Op.PREFIX, "io.kestra", "exec-alpha", "exec-beta"),

        // START_DATE / END_DATE (TIME; both map to timestamp)
        leaf(Group.TIME, Field.START_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW_ZDT, "exec-now", "exec-future"),
        leaf(Group.TIME, Field.START_DATE, Op.GREATER_THAN, T_NOW_ZDT, "exec-future"),
        leaf(Group.TIME, Field.START_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW_ZDT, "exec-past", "exec-now"),
        leaf(Group.TIME, Field.START_DATE, Op.LESS_THAN, T_NOW_ZDT, "exec-past"),
        leaf(Group.TIME, Field.START_DATE, Op.EQUALS, T_NOW_ZDT, "exec-now"),
        leaf(Group.TIME, Field.START_DATE, Op.NOT_EQUALS, T_NOW_ZDT, "exec-past", "exec-future"),
        leaf(Group.TIME, Field.END_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW_ZDT, "exec-now", "exec-future"),
        leaf(Group.TIME, Field.END_DATE, Op.GREATER_THAN, T_NOW_ZDT, "exec-future"),
        leaf(Group.TIME, Field.END_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW_ZDT, "exec-past", "exec-now"),
        leaf(Group.TIME, Field.END_DATE, Op.LESS_THAN, T_NOW_ZDT, "exec-past"),
        leaf(Group.TIME, Field.END_DATE, Op.EQUALS, T_NOW_ZDT, "exec-now"),
        leaf(Group.TIME, Field.END_DATE, Op.NOT_EQUALS, T_NOW_ZDT, "exec-past", "exec-future"),

        // FLOW_ID (DISTINCT)
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.EQUALS, "alpha-flow", "exec-alpha"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.NOT_EQUALS, "alpha-flow", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.CONTAINS, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.STARTS_WITH, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.ENDS_WITH, "-flow", "exec-alpha", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.REGEX, "alpha-.*", "exec-alpha"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.IN, List.of("alpha-flow", "beta-flow"), "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.NOT_IN, List.of("alpha-flow", "beta-flow"), "exec-gamma"),
        leaf(Group.DISTINCT, Field.FLOW_ID, Op.PREFIX, "alpha-flow", "exec-alpha"),

        // TRIGGER_ID (DISTINCT)
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.EQUALS, "alpha-trigger", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.NOT_EQUALS, "alpha-trigger", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.CONTAINS, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.STARTS_WITH, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.ENDS_WITH, "-trigger", "exec-alpha", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.IN, List.of("alpha-trigger", "beta-trigger"), "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.TRIGGER_ID, Op.NOT_IN, List.of("alpha-trigger", "beta-trigger"), "exec-gamma"),

        // EXECUTION_ID (DISTINCT)
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.EQUALS, "exec-alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.NOT_EQUALS, "exec-alpha", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.CONTAINS, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.STARTS_WITH, "exec-", "exec-alpha", "exec-beta", "exec-gamma"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.ENDS_WITH, "alpha", "exec-alpha"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.IN, List.of("exec-alpha", "exec-beta"), "exec-alpha", "exec-beta"),
        leaf(Group.DISTINCT, Field.EXECUTION_ID, Op.NOT_IN, List.of("exec-alpha", "exec-beta"), "exec-gamma"),

        // KIND (KIND; explicit KIND overrides the NORMAL-only default of find)
        leaf(Group.KIND, Field.KIND, Op.EQUALS, ExecutionKind.PLAYGROUND.name(), "exec-playground-kind"),
        leaf(Group.KIND, Field.KIND, Op.NOT_EQUALS, ExecutionKind.PLAYGROUND.name(), "exec-normal-kind", "exec-loop-kind"),
        leaf(Group.KIND, Field.KIND, Op.IN, List.of(ExecutionKind.PLAYGROUND.name(), ExecutionKind.LOOP.name()), "exec-playground-kind", "exec-loop-kind"),
        leaf(Group.KIND, Field.KIND, Op.NOT_IN, List.of(ExecutionKind.PLAYGROUND.name(), ExecutionKind.LOOP.name()), "exec-normal-kind"),

        // Composed (nested logical) — the full composition space: AND, OR, AND-of-3, OR-across-fields, nested AND(OR).
        new FilterCase(
            Group.DISTINCT,
            and(cond(Field.NAMESPACE, Op.STARTS_WITH, "io.kestra"), cond(Field.FLOW_ID, Op.ENDS_WITH, "-flow")),
            List.of("exec-alpha", "exec-beta")
        ),
        new FilterCase(
            Group.DISTINCT,
            or(cond(Field.EXECUTION_ID, Op.EQUALS, "exec-alpha"), cond(Field.EXECUTION_ID, Op.EQUALS, "exec-gamma")),
            List.of("exec-alpha", "exec-gamma")
        ),
        new FilterCase(
            Group.DISTINCT,
            and(cond(Field.NAMESPACE, Op.CONTAINS, "kestra"), cond(Field.FLOW_ID, Op.STARTS_WITH, "alpha"), cond(Field.TRIGGER_ID, Op.EQUALS, "alpha-trigger")),
            List.of("exec-alpha")
        ),
        new FilterCase(
            Group.DISTINCT,
            or(cond(Field.TASK_ID, Op.EQUALS, "alpha-task"), cond(Field.FLOW_ID, Op.EQUALS, "beta-flow")),
            List.of("exec-alpha", "exec-beta")
        ),
        new FilterCase(
            Group.DISTINCT,
            and(cond(Field.NAMESPACE, Op.CONTAINS, "kestra"), or(cond(Field.FLOW_ID, Op.EQUALS, "alpha-flow"), cond(Field.FLOW_ID, Op.EQUALS, "beta-flow"))),
            List.of("exec-alpha", "exec-beta")
        )
    );

    @ParameterizedTest
    @FieldSource("filterCases")
    void find_withFilter(FilterCase testCase) {
        List<LogEntry> results = logDataStore.find(Pageable.UNPAGED, tenantOf(testCase.group()), List.of(testCase.filter())).getContent();

        assertThat(results)
            .extracting(LogEntry::getExecutionId)
            .containsExactlyInAnyOrderElementsOf(testCase.expected());
    }

    @Test
    void find_defaultsToNormalKindWhenNoKindFilter() {
        List<LogEntry> results = logDataStore.find(Pageable.UNPAGED, kindTenant, null).getContent();

        assertThat(results).extracting(LogEntry::getExecutionId).containsExactly("exec-normal-kind");
    }

    static Stream<QueryFilter> unsupportedFilters() {
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

    @ParameterizedTest
    @MethodSource("unsupportedFilters")
    void find_rejectsUnsupportedFilter(QueryFilter filter) {
        assertThrows(InvalidQueryFiltersException.class, () -> logDataStore.find(Pageable.UNPAGED, distinctTenant, List.of(filter)));
    }

    @Test
    void findAsync_appliesFilter() {
        List<LogEntry> results = logDataStore.findAsync(
            distinctTenant, List.of(
                cond(Field.NAMESPACE, Op.STARTS_WITH, "io.kestra")
            )
        ).collectList().block();

        assertThat(results).extracting(LogEntry::getExecutionId).containsExactlyInAnyOrder("exec-alpha", "exec-beta");
    }

    @Test
    void findAllAsync_returnsEveryKindForTenant() {
        // findAllAsync ignores the NORMAL-kind default (used for backups): every kind comes back.
        List<LogEntry> results = logDataStore.findAllAsync(kindTenant).collectList().block();

        assertThat(results).extracting(LogEntry::getExecutionId)
            .containsExactlyInAnyOrder("exec-normal-kind", "exec-playground-kind", "exec-loop-kind");
    }

    // ------------------------------------------------------------------ findByExecutionId* family (FAMILY fixture)

    @Test
    void findByExecutionId_variants() {
        // full execution (all attempts) with and without ACL
        assertThat(logDataStore.findByExecutionId(familyTenant, FAMILY_EXEC, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdWithoutAcl(familyTenant, FAMILY_EXEC, null)).hasSize(3);

        // min-level actually filters
        assertThat(logDataStore.findByExecutionId(familyTenant, FAMILY_EXEC, Level.WARN))
            .extracting(LogEntry::getLevel).containsExactlyInAnyOrder(Level.WARN, Level.ERROR);

        // namespace + flow scoping: correct pair matches, a wrong flow matches nothing
        assertThat(logDataStore.findByExecutionId(familyTenant, FAMILY_NS, FAMILY_FLOW, FAMILY_EXEC, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionId(familyTenant, FAMILY_NS, "wrong-flow", FAMILY_EXEC, null)).isEmpty();
    }

    @Test
    void findByExecutionIdAndTaskId_variants() {
        assertThat(logDataStore.findByExecutionIdAndTaskId(familyTenant, FAMILY_EXEC, FAMILY_TASK, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdAndTaskIdWithoutAcl(familyTenant, FAMILY_EXEC, FAMILY_TASK, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdAndTaskId(familyTenant, FAMILY_NS, FAMILY_FLOW, FAMILY_EXEC, FAMILY_TASK, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdAndTaskId(familyTenant, FAMILY_EXEC, FAMILY_TASK, Level.ERROR))
            .extracting(LogEntry::getLevel).containsExactly(Level.ERROR);
    }

    @Test
    void findByExecutionIdAndTaskRunId_variants() {
        assertThat(logDataStore.findByExecutionIdAndTaskRunId(familyTenant, FAMILY_EXEC, FAMILY_TR, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdAndTaskRunIdWithoutAcl(familyTenant, FAMILY_EXEC, FAMILY_TR, null)).hasSize(3);
        assertThat(logDataStore.findByExecutionIdAndTaskRunId(familyTenant, FAMILY_EXEC, FAMILY_TR, Level.ERROR))
            .extracting(LogEntry::getLevel).containsExactly(Level.ERROR);
    }

    @Test
    void findByExecutionIdAndTaskRunIdAndAttempt_variants() {
        assertThat(logDataStore.findByExecutionIdAndTaskRunIdAndAttempt(familyTenant, FAMILY_EXEC, FAMILY_TR, null, 1))
            .extracting(LogEntry::getLevel).containsExactly(Level.WARN);
        assertThat(logDataStore.findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(familyTenant, FAMILY_EXEC, FAMILY_TR, null, 2))
            .extracting(LogEntry::getLevel).containsExactly(Level.ERROR);

        // minLevel composes with the attempt filter: attempt 1 is WARN, so a WARN floor keeps it but an ERROR floor drops it.
        assertThat(logDataStore.findByExecutionIdAndTaskRunIdAndAttempt(familyTenant, FAMILY_EXEC, FAMILY_TR, Level.WARN, 1))
            .extracting(LogEntry::getLevel).containsExactly(Level.WARN);
        assertThat(logDataStore.findByExecutionIdAndTaskRunIdAndAttempt(familyTenant, FAMILY_EXEC, FAMILY_TR, Level.ERROR, 1))
            .isEmpty();
    }

    @Test
    void findByExecutionId_returnsNonNormalKind() {
        // findByExecutionId does not apply the NORMAL-only default, so a PLAYGROUND execution is returned.
        assertThat(logDataStore.findByExecutionId(kindTenant, "exec-playground-kind", null))
            .extracting(LogEntry::getExecutionKind).containsExactly(ExecutionKind.PLAYGROUND);
    }

    // ------------------------------------------------------------------ pagination (offset or cursor)

    @Test
    void pageable() {
        if (logDataStore.paginationType() == PaginationType.OFFSET) {
            Page<LogEntry> find = logDataStore.findByExecutionId(pageTenant, PAGE_EXEC, null, Pageable.from(1, 50));
            assertThat(find.getNumberOfElements()).isEqualTo(50);
            assertThat(find.getTotalSize()).isEqualTo(102L);

            find = logDataStore.findByExecutionId(pageTenant, PAGE_EXEC, null, Pageable.from(3, 50));
            assertThat(find.getNumberOfElements()).isEqualTo(2);
            assertThat(find.getTotalSize()).isEqualTo(102L);

            find = logDataStore.findByExecutionIdAndTaskId(pageTenant, PAGE_EXEC, "taskId2", null, Pageable.from(1, 50));
            assertThat(find.getNumberOfElements()).isEqualTo(22);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logDataStore.findByExecutionIdAndTaskRunId(pageTenant, PAGE_EXEC, "taskRunId2", null, Pageable.from(1, 10));
            assertThat(find.getNumberOfElements()).isEqualTo(10);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logDataStore.findByExecutionIdAndTaskRunIdAndAttempt(pageTenant, PAGE_EXEC, "taskRunId2", null, 0, Pageable.from(1, 10));
            assertThat(find.getNumberOfElements()).isEqualTo(10);
            assertThat(find.getTotalSize()).isEqualTo(22L);

            find = logDataStore.findByExecutionIdAndTaskRunId(pageTenant, PAGE_EXEC, "taskRunId2", null, Pageable.from(10, 10));
            assertThat(find.getNumberOfElements()).isZero();
        } else {
            List<QueryFilter> filters = List.of(cond(Field.EXECUTION_ID, Op.EQUALS, PAGE_EXEC));
            int pageSize = 20;
            int collected = 0;
            Pageable.Cursor cursor = null;
            for (int guard = 0; guard < 100; guard++) {
                Pageable request = cursor == null
                    ? Pageable.from(1, pageSize)
                    : Pageable.afterCursor(cursor, 1, pageSize, Sort.UNSORTED);

                Page<LogEntry> page = logDataStore.find(request, pageTenant, filters);
                assertThat(page).isInstanceOf(CursoredPage.class);
                assertThat(page.hasTotalSize()).isFalse();

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

    // ------------------------------------------------------------------ aggregation (or graceful degradation)

    @Test
    void fetchData() throws Exception {
        var results = logDataStore.fetchData(
            distinctTenant,
            Logs.builder().type(Logs.class.getName())
                .columns(Map.of("count", ColumnDescriptor.<Logs.Fields> builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build()))
                .build(),
            ZonedDateTime.now().minusYears(10), ZonedDateTime.now().plusYears(10), null
        );

        if (logDataStore.canAggregate()) {
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().get("count")).isIn(3, 3L); // alpha, beta, gamma
        } else {
            assertThat(results).isEmpty();
        }
    }

    @Test
    void fetchValue() throws Exception {
        var result = logDataStore.fetchValue(
            distinctTenant,
            LogsKPI.builder().type(LogsKPI.class.getName())
                .columns(ColumnDescriptor.<Logs.Fields> builder().field(Logs.Fields.LEVEL).agg(AggregationType.COUNT).build())
                .build(),
            ZonedDateTime.now().minusYears(10), ZonedDateTime.now().plusYears(10), false
        );

        assertThat(result).isEqualTo(logDataStore.canAggregate() ? 3.0 : 0.0);
    }

    // ------------------------------------------------------------------ purge / delete (self-provisioned; canPurge branch)

    @Test
    void purge_single() {
        String tenant = randomTenant();
        logDataStore.saveBatch(List.of(log(Level.INFO, "purge-one").tenantId(tenant).build(), log(Level.WARN, "purge-one").tenantId(tenant).build()));
        awaitVisible(tenant, "purge-one");

        int deleted = logDataStore.purge(Execution.builder().id("purge-one").build());

        if (logDataStore.canPurge()) {
            assertThat(deleted).isEqualTo(2);
            assertThat(logDataStore.findByExecutionId(tenant, "purge-one", null)).isEmpty();
        } else {
            assertThat(deleted).isZero();
            assertThat(logDataStore.findByExecutionId(tenant, "purge-one", null)).hasSize(2);
        }
    }

    @Test
    void purge_multiple() {
        String tenant = randomTenant();
        logDataStore.saveBatch(
            List.of(
                log(Level.INFO, "purge-a").tenantId(tenant).build(),
                log(Level.INFO, "purge-a").tenantId(tenant).build(),
                log(Level.INFO, "purge-b").tenantId(tenant).build()
            )
        );
        awaitVisible(tenant, "purge-b");

        int deleted = logDataStore.purge(List.of(Execution.builder().id("purge-a").build(), Execution.builder().id("purge-b").build()));

        if (logDataStore.canPurge()) {
            assertThat(deleted).isEqualTo(3);
        } else {
            assertThat(deleted).isZero();
            assertThat(logDataStore.findByExecutionId(tenant, "purge-a", null)).hasSize(2);
        }
    }

    @Test
    void deleteByQuery_byExecutionTaskRunAttempt() {
        String tenant = randomTenant();
        logDataStore.save(log(Level.WARN, "del-exec").tenantId(tenant).taskId("del-task").taskRunId("del-tr").attemptNumber(0).build());
        awaitVisible(tenant, "del-exec");

        logDataStore.deleteByQuery(tenant, "del-exec", "del-task", "del-tr", Level.INFO, 0);
        assertRemaining(tenant, "del-exec", 1);
    }

    @Test
    void deleteByQuery_byNamespaceFlowTrigger() {
        String tenant = randomTenant();
        logDataStore.save(log(Level.INFO, "del-trigger").tenantId(tenant).namespace("io.kestra.del").flowId("del-flow").triggerId("del-trigger-id").build());
        awaitVisible(tenant, "del-trigger");

        logDataStore.deleteByQuery(tenant, "io.kestra.del", "del-flow", "del-trigger-id");
        assertRemaining(tenant, "del-trigger", 1);
    }

    @Test
    void deleteByQuery_retentionByBatchSize() {
        String tenant = randomTenant();
        List<LogEntry> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(log(Level.INFO, "del-batch").tenantId(tenant).namespace("io.kestra.del").flowId("del-flow").build());
        }
        logDataStore.saveBatch(batch);
        awaitVisible(tenant, "del-batch");

        int deleted = logDataStore.deleteByQuery(tenant, "io.kestra.del", "del-flow", null, List.of(Level.INFO), null, ZonedDateTime.now().plusMinutes(1), true, true, 2);

        if (logDataStore.canPurge()) {
            assertThat(deleted).isEqualTo(5);
            assertThat(logDataStore.findByExecutionId(tenant, "del-batch", null)).isEmpty();
        } else {
            assertThat(deleted).isZero();
            assertThat(logDataStore.findByExecutionId(tenant, "del-batch", null)).hasSize(5);
        }
    }

    @Test
    void deleteByQuery_retentionByNamespacePrefix() {
        String tenant = randomTenant();
        logDataStore.saveBatch(
            List.of(
                log(Level.INFO, "del-parent").tenantId(tenant).namespace("io.kestra.purge").build(),
                log(Level.INFO, "del-child").tenantId(tenant).namespace("io.kestra.purge.child").build(),
                log(Level.INFO, "del-sibling").tenantId(tenant).namespace("io.kestra.purgeother").build()
            )
        );
        awaitVisible(tenant, "del-sibling");

        // Without a flowId the namespace is a prefix: the namespace and its descendants match, textual siblings do not.
        int deleted = logDataStore.deleteByQuery(tenant, "io.kestra.purge", null, null, null, null, ZonedDateTime.now().plusMinutes(1), true, true, null);

        if (logDataStore.canPurge()) {
            assertThat(deleted).isEqualTo(2);
            assertThat(logDataStore.findByExecutionId(tenant, "del-parent", null)).isEmpty();
            assertThat(logDataStore.findByExecutionId(tenant, "del-child", null)).isEmpty();
            assertThat(logDataStore.findByExecutionId(tenant, "del-sibling", null)).hasSize(1);
        } else {
            assertThat(deleted).isZero();
            assertThat(logDataStore.findByExecutionId(tenant, "del-parent", null)).hasSize(1);
        }
    }

    @Test
    void deleteByFilters() {
        String tenant = randomTenant();
        logDataStore.save(log(Level.INFO, "delfilter-exec").tenantId(tenant).build());
        awaitVisible(tenant, "delfilter-exec");

        logDataStore.deleteByFilters(tenant, List.of(cond(Field.EXECUTION_ID, Op.EQUALS, "delfilter-exec")));
        assertRemaining(tenant, "delfilter-exec", 1);
    }

    private void assertRemaining(String tenant, String executionId, int savedCount) {
        if (logDataStore.canPurge()) {
            assertThat(logDataStore.findByExecutionId(tenant, executionId, null)).isEmpty();
        } else {
            assertThat(logDataStore.findByExecutionId(tenant, executionId, null)).hasSize(savedCount);
        }
    }
}
