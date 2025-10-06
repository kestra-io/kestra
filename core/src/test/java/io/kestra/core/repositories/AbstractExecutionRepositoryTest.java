package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@KestraTest
public abstract class AbstractExecutionRepositoryTest {
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    public static Execution.ExecutionBuilder builder(String tenantId, State.Type state, String flowId) {
        return builder(tenantId, state, flowId, NAMESPACE);
    }

    public static Execution.ExecutionBuilder builder(String tenantId, State.Type state, String flowId, String namespace) {
        State finalState = randomDuration(state);

        Execution.ExecutionBuilder execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(namespace)
            .tenantId(tenantId)
            .flowId(flowId == null ? FLOW : flowId)
            .flowRevision(1)
            .state(finalState);


        List<TaskRun> taskRuns = Arrays.asList(
            TaskRun.of(execution.build(), ResolvedTask.of(
                    Return.builder().id("first").type(Return.class.getName()).format(Property.ofValue("test")).build())
                )
                .withState(State.Type.SUCCESS),
            spyTaskRun(TaskRun.of(execution.build(), ResolvedTask.of(
                        Return.builder().id("second").type(Return.class.getName()).format(Property.ofValue("test")).build())
                    )
                    .withState(state),
                state
            ),
            TaskRun.of(execution.build(), ResolvedTask.of(
                Return.builder().id("third").type(Return.class.getName()).format(Property.ofValue("test")).build())).withState(state)
        );

        if (flowId == null) {
            return execution.taskRunList(List.of(taskRuns.getFirst(), taskRuns.get(1), taskRuns.get(2)));
        }

        return execution.taskRunList(List.of(taskRuns.getFirst(), taskRuns.get(1)));
    }


    static TaskRun spyTaskRun(TaskRun taskRun, State.Type state) {
        TaskRun spy = spy(taskRun);

        doReturn(randomDuration(state))
            .when(spy)
            .getState();

        return spy;
    }

    static State randomDuration(State.Type state) {
        State finalState = new State();

        finalState = spy(finalState
            .withState(state != null ? state : State.Type.SUCCESS)
        );

        Random rand = new Random();
        doReturn(Duration.ofSeconds(rand.nextInt(150)))
            .when(finalState)
            .getDuration();

        return finalState;
    }

    protected void inject(String tenantId) {
        inject(tenantId, null);
    }

    protected void inject(String tenantId, String executionTriggerId) {
        ExecutionTrigger executionTrigger = null;

        if (executionTriggerId != null) {
            executionTrigger = ExecutionTrigger.builder()
                .variables(Map.of("executionId", executionTriggerId))
                .build();
        }

        executionRepository.save(builder(tenantId, State.Type.RUNNING, null)
            .labels(List.of(
                new Label("key", "value"),
                new Label("key2", "value2")
            ))
            .trigger(executionTrigger)
            .build()
        );
        for (int i = 1; i < 28; i++) {
            executionRepository.save(builder(
                tenantId,
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).trigger(executionTrigger).build());
        }

        // add a test execution, this should be ignored in search & statistics
        executionRepository.save(builder(
            tenantId,
            State.Type.SUCCESS,
            null
        )
            .trigger(executionTrigger)
            .kind(ExecutionKind.TEST)
            .build());
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter, int expectedSize){
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant, "executionTriggerId");

        ArrayListTotal<Execution> entries = executionRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

        assertThat(entries).hasSize(expectedSize);
    }

    static Stream<Arguments> filterCombinations() {
        return Stream.of(
            Arguments.of(QueryFilter.builder().field(Field.QUERY).value("unittest").operation(Op.EQUALS).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).value(List.of(USER)).operation(Op.EQUALS).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.unittest").operation(Op.EQUALS).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(), 1),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value(FLOW).operation(Op.EQUALS).build(), 15),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.STATE).value(Type.RUNNING).operation(Op.EQUALS).build(), 5),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("executionTriggerId").operation(Op.EQUALS).build(), 28),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(), 28)
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter){
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        assertThrows(InvalidQueryFiltersException.class, () -> executionRepository.find(Pageable.UNPAGED, tenant, List.of(filter)));
    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.DEBUG).operation(Op.EQUALS).build()
        );
    }

    @Test
    protected void find() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant);

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10),  tenant, null);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);

        List<QueryFilter> filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.STATE)
            .operation(QueryFilter.Op.EQUALS)
            .value( List.of(State.Type.RUNNING, State.Type.FAILED))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(8L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value"))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(1L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value2"))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(0L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value", "keyTest", "valueTest"))
            .build()
        );
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(0L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.FLOW_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value("second")
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(13L);

        filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value("second")
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.NAMESPACE)
                .operation(QueryFilter.Op.EQUALS)
                .value(NAMESPACE)
                .build()
        );
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(13L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.NAMESPACE)
            .operation(QueryFilter.Op.STARTS_WITH)
            .value("io.kestra")
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
    }

    @Test
    protected void findTriggerExecutionId() {
        String executionTriggerId = IdUtils.create();

        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant, executionTriggerId);
        inject(tenant);

        var filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.TRIGGER_EXECUTION_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value(executionTriggerId)
            .build());
        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10), tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);
        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.CHILD)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.MAIN)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters );
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger()).isNull();

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, null);
        assertThat(executions.getTotal()).isEqualTo(56L);
    }

    @Test
    protected void findWithSort() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant);

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))),  tenant, null);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);

        var filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.STATE)
            .operation(QueryFilter.Op.EQUALS)
            .value(List.of(State.Type.RUNNING, State.Type.FAILED))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(8L);
    }

    @Test
    protected void findById() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var execution1 = ExecutionFixture.EXECUTION_1(tenant);
        executionRepository.save(execution1);

        Optional<Execution> full = executionRepository.findById(tenant, execution1.getId());
        assertThat(full.isPresent()).isTrue();

        full.ifPresent(current -> {
            assertThat(full.get().getId()).isEqualTo(execution1.getId());
        });
    }

    @Test
    protected void shouldFindByIdTestExecution() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var executionTest = ExecutionFixture.EXECUTION_TEST(tenant);
        executionRepository.save(executionTest);

        Optional<Execution> full = executionRepository.findById(tenant, executionTest.getId());
        assertThat(full.isPresent()).isTrue();

        full.ifPresent(current -> {
            assertThat(full.get().getId()).isEqualTo(executionTest.getId());
        });
    }

    @Test
    protected void purge() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var execution1 = ExecutionFixture.EXECUTION_1(tenant);
        executionRepository.save(execution1);

        Optional<Execution> full = executionRepository.findById(tenant, execution1.getId());
        assertThat(full.isPresent()).isTrue();

        executionRepository.purge(execution1);

        full = executionRepository.findById(tenant, execution1.getId());
        assertThat(full.isPresent()).isFalse();
    }

    @Test
    protected void purgeExecutions() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var execution1 = ExecutionFixture.EXECUTION_1(tenant);
        executionRepository.save(execution1);
        var execution2 = ExecutionFixture.EXECUTION_2(tenant);
        executionRepository.save(execution2);

        var results = executionRepository.purge(List.of(execution1, execution2));
        assertThat(results).isEqualTo(2);

        assertThat(executionRepository.findById(tenant, execution1.getId())).isEmpty();
        assertThat(executionRepository.findById(tenant, execution2.getId())).isEmpty();
    }

    @Test
    protected void delete() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var execution1 = ExecutionFixture.EXECUTION_1(tenant);
        executionRepository.save(execution1);

        Optional<Execution> full = executionRepository.findById(tenant, execution1.getId());
        assertThat(full.isPresent()).isTrue();

        executionRepository.delete(execution1);

        full = executionRepository.findById(tenant, execution1.getId());
        assertThat(full.isPresent()).isFalse();
    }

    @Test
    protected void mappingConflict() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        executionRepository.save(ExecutionFixture.EXECUTION_2(tenant));
        executionRepository.save(ExecutionFixture.EXECUTION_1(tenant));

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(tenant, NAMESPACE, FLOW, Pageable.from(1, 10));

        assertThat(page1.size()).isEqualTo(2);
    }

    @Test
    protected void dailyStatistics() throws InterruptedException {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                tenant,
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        executionRepository.save(builder(
            tenant,
            State.Type.SUCCESS,
            "second"
        ).namespace(NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE).build());

        // mysql need some time ...
        Thread.sleep(500);

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            null,
            tenant,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().size()).isEqualTo(11);
        assertThat(result.get(10).getDuration().getAvg().toMillis()).isGreaterThan(0L);

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED)).isEqualTo(3L);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING)).isEqualTo(5L);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(21L);

        result = executionRepository.dailyStatistics(
            null,
            tenant,
            List.of(FlowScope.USER, FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(21L);

        result = executionRepository.dailyStatistics(
            null,
            tenant,
            List.of(FlowScope.USER),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(20L);

        result = executionRepository.dailyStatistics(
            null,
            tenant,
            List.of(FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    protected void executionsCount() throws InterruptedException {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        for (int i = 0; i < 14; i++) {
            executionRepository.save(builder(
                tenant,
                State.Type.SUCCESS,
                i < 2 ? "first" : (i < 5 ? "second" : "third")
            ).build());
        }

        // mysql need some time ...
        Thread.sleep(500);

        List<ExecutionCount> result = executionRepository.executionCounts(
            tenant,
            List.of(
                new Flow(NAMESPACE, "first"),
                new Flow(NAMESPACE, "second"),
                new Flow(NAMESPACE, "third"),
                new Flow(NAMESPACE, "missing")
            ),
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null
        );
        assertThat(result.size()).isEqualTo(4);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount()).isEqualTo(2L);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount()).isEqualTo(3L);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount()).isEqualTo(9L);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("missing")).findFirst().get().getCount()).isEqualTo(0L);

        result = executionRepository.executionCounts(
            tenant,
            List.of(
                new Flow(NAMESPACE, "first"),
                new Flow(NAMESPACE, "second"),
                new Flow(NAMESPACE, "third")
            ),
            List.of(State.Type.SUCCESS),
            null,
            null,
            null
        );
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount()).isEqualTo(2L);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount()).isEqualTo(3L);
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount()).isEqualTo(9L);

        result = executionRepository.executionCounts(
            tenant,
            null,
            null,
            null,
            null,
            List.of(NAMESPACE)
        );
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.stream().filter(executionCount -> executionCount.getNamespace().equals(NAMESPACE)).findFirst().get().getCount()).isEqualTo(14L);
    }

    @Test
    protected void update() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = ExecutionFixture.EXECUTION_1(tenant);
        executionRepository.save(execution);

        Label label = new Label("key", "value");
        Execution updated = execution.toBuilder().labels(List.of(label)).build();
        executionRepository.update(updated);

        Optional<Execution> validation = executionRepository.findById(tenant, updated.getId());
        assertThat(validation.isPresent()).isTrue();
        assertThat(validation.get().getLabels().size()).isEqualTo(1);
        assertThat(validation.get().getLabels().getFirst()).isEqualTo(label);
    }

    @Test
    void shouldFindLatestExecutionGivenState() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution earliest = buildWithCreatedDate(tenant, Instant.now().minus(Duration.ofMinutes(10)));
        Execution latest = buildWithCreatedDate(tenant, Instant.now().minus(Duration.ofMinutes(5)));

        executionRepository.save(earliest);
        executionRepository.save(latest);

        Optional<Execution> result = executionRepository.findLatestForStates(tenant, "io.kestra.unittest", "full", List.of(State.Type.CREATED));
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    protected void fetchData() throws IOException {
        String tenantId = "data-tenant";
        Execution execution = Execution.builder()
            .tenantId(tenantId)
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("some-execution")
            .flowRevision(1)
            .labels(Label.from(Map.of("country", "FR")))
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, Instant.now()))))
            .taskRunList(List.of())
            .build();

        execution = executionRepository.save(execution);

        ArrayListTotal<Map<String, Object>> data = executionRepository.fetchData(tenantId, Executions.builder()
                .type(Executions.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).agg(AggregationType.COUNT).build(),
                    "country", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.LABELS).labelKey("country").build(),
                    "date", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.START_DATE).build()
                )).build(),
            ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
            ZonedDateTime.now(),
            null
        );

        assertThat(data.getTotal()).isEqualTo(1L);
        assertThat(data.get(0).get("count")).isEqualTo(1L);
        assertThat(data.get(0).get("country")).isEqualTo("FR");
        Instant startDate = execution.getState().getStartDate();
        assertThat(data.get(0).get("date")).isEqualTo(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(ZonedDateTime.ofInstant(startDate, ZoneId.systemDefault()).withSecond(0).withNano(0)));
    }

    private static Execution buildWithCreatedDate(String tenant, Instant instant) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tenantId(tenant)
            .flowId("full")
            .flowRevision(1)
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, instant))))
            .inputs(ImmutableMap.of("test", "value"))
            .taskRunList(List.of())
            .build();
    }

    @Test
    protected void findAllAsync() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
inject(tenant);

        List<Execution> executions = executionRepository.findAllAsync(tenant).collectList().block();
        assertThat(executions).hasSize(29); // used by the backup so it contains TEST executions
    }

    @Test
    protected void shouldFindByLabel() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
inject(tenant);

        List<QueryFilter> filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value"))
            .build());
        List<Execution> executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.size()).isEqualTo(1L);

        // Filtering by two pairs of labels, since now its a and behavior, it should not return anything
        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value", "keyother", "valueother"))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.size()).isEqualTo(0L);
    }

    @Test
    protected void shouldReturnLastExecutionsWhenInputsAreNull() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant);

        List<Execution> lastExecutions = executionRepository.lastExecutions(tenant, null);

        assertThat(lastExecutions).isNotEmpty();
        Set<String> flowIds = lastExecutions.stream().map(Execution::getFlowId).collect(Collectors.toSet());
        assertThat(flowIds.size()).isEqualTo(lastExecutions.size());
    }

}
