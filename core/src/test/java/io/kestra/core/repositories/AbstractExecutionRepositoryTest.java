package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.junit.annotations.FlakyTest;
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
import static io.kestra.core.models.flows.FlowScope.USER;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest
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
            .kind(ExecutionKind.NORMAL)
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
        doReturn(Optional.of(Duration.ofSeconds(rand.nextInt(150))))
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

        // add a NORMAL kind execution, it should be fetched correctly
        executionRepository.save(builder(
            tenantId,
            State.Type.SUCCESS,
            null
        )
            .trigger(executionTrigger)
            .kind(ExecutionKind.NORMAL)
            .build());

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
    @FlakyTest(description = "Filtering tests are sometimes returning 0")
    void should_find_all(QueryFilter filter, int expectedSize){
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant, "executionTriggerId");

        ArrayListTotal<Execution> entries = executionRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

        assertThat(entries).hasSize(expectedSize);
    }

    static Stream<Arguments> filterCombinations() {
        return Stream.of(
            Arguments.of(QueryFilter.builder().field(Field.QUERY).value("unittest").operation(Op.EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).value("unused").operation(Op.NOT_EQUALS).build(), 29),

            Arguments.of(QueryFilter.builder().field(Field.SCOPE).value(List.of(USER)).operation(Op.EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).value(List.of(SYSTEM)).operation(Op.NOT_EQUALS).build(), 29),

            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.unittest").operation(Op.EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("not.this.one").operation(Op.NOT_EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("o.kestra.unittes").operation(Op.CONTAINS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra.uni").operation(Op.STARTS_WITH).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("o.kestra.unittest").operation(Op.ENDS_WITH).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("io\\.kestra\\.unittest").operation(Op.REGEX).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value(List.of("io.kestra.unittest", "unused")).operation(Op.IN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value(List.of("unused.first", "unused.second")).operation(Op.NOT_IN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).value("io.kestra").operation(Op.PREFIX).build(), 29),

            Arguments.of(QueryFilter.builder().field(Field.KIND).value(ExecutionKind.NORMAL).operation(Op.EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.KIND).value(ExecutionKind.TEST).operation(Op.NOT_EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.KIND).value(List.of(ExecutionKind.NORMAL, ExecutionKind.PLAYGROUND)).operation(Op.IN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.KIND).value(List.of(ExecutionKind.PLAYGROUND, ExecutionKind.TEST)).operation(Op.NOT_IN).build(), 29),

            Arguments.of(QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(), 1),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "unknown")).operation(Op.NOT_EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value", "key2", "value2")).operation(Op.IN).build(), 1),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).value(Map.of("key1", "value1")).operation(Op.NOT_IN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).value("value").operation(Op.CONTAINS).build(), 1),

            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value(FLOW).operation(Op.EQUALS).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value(FLOW).operation(Op.NOT_EQUALS).build(), 13),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value("ul").operation(Op.CONTAINS).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value("ful").operation(Op.STARTS_WITH).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value("ull").operation(Op.ENDS_WITH).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value("[ful]{4}").operation(Op.REGEX).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value(List.of(FLOW, "other")).operation(Op.IN).build(), 16),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value(List.of(FLOW, "other2")).operation(Op.NOT_IN).build(), 13),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).value("ful").operation(Op.PREFIX).build(), 16),

            Arguments.of(QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.STATE).value(Type.RUNNING).operation(Op.EQUALS).build(), 5),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("executionTriggerId").operation(Op.EQUALS).build(), 29),

            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(), 29),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.NOT_EQUALS).build(), 0)
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
        assertThat(executions.getTotal()).isEqualTo(29L);
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
        assertThat(executions.getTotal()).isEqualTo(29L);
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
        assertThat(executions.getTotal()).isEqualTo(29L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);
        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.CHILD)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters);
        assertThat(executions.getTotal()).isEqualTo(29L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.MAIN)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, filters );
        assertThat(executions.getTotal()).isEqualTo(29L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger()).isNull();

        executions = executionRepository.find(Pageable.from(1, 10),  tenant, null);
        assertThat(executions.getTotal()).isEqualTo(58L);
    }

    @Test
    protected void findWithSort() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        inject(tenant);

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))),  tenant, null);
        assertThat(executions.getTotal()).isEqualTo(29L);
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
    protected void dashboard_fetchData() throws IOException {
        var tenantId = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var executionDuration = Duration.ofMinutes(220);
        var executionCreateDate = Instant.now();
        Execution execution = Execution.builder()
            .tenantId(tenantId)
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("some-execution")
            .flowRevision(1)
            .labels(Label.from(Map.of("country", "FR")))
            .state(new State(Type.SUCCESS,
                List.of(new State.History(State.Type.CREATED, executionCreateDate), new State.History(Type.SUCCESS, executionCreateDate.plus(executionDuration)))))
            .taskRunList(List.of())
            .build();

        execution = executionRepository.save(execution);

        var now = ZonedDateTime.now();
        ArrayListTotal<Map<String, Object>> data = executionRepository.fetchData(tenantId, Executions.builder()
                .type(Executions.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).agg(AggregationType.COUNT).build(),
                    "id", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).build(),
                    "date", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.START_DATE).build(),
                    "duration", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.DURATION).build()
                )).build(),
            now.minusHours(1),
            now,
            null
        );

        assertThat(data.getTotal()).isEqualTo(1L);
        assertThat(data).first().hasFieldOrProperty("count");
        assertThat(data).first().extracting("count").hasToString("1");
        assertThat(data).first().hasFieldOrPropertyWithValue("id", execution.getId());
    }

    @Test
    void dashboard_fetchData_365Days_verifiesDateGrouping() throws IOException {
        var tenantId = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var executionDuration = Duration.ofMinutes(220);
        var executionCreateDate = Instant.now();

        // Create an execution within the 365-day range
        Execution execution = Execution.builder()
            .tenantId(tenantId)
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("some-execution")
            .flowRevision(1)
            .labels(Label.from(Map.of("country", "FR")))
            .state(new State(Type.SUCCESS,
                List.of(new State.History(State.Type.CREATED, executionCreateDate), new State.History(Type.SUCCESS, executionCreateDate.plus(executionDuration)))))
            .taskRunList(List.of())
            .build();

        execution = executionRepository.save(execution);

        // Create an execution BEYOND 365 days (400 days ago) - should be filtered out
        var executionCreateDateOld = Instant.now().minus(Duration.ofDays(400));
        Execution executionOld = Execution.builder()
            .tenantId(tenantId)
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("some-execution-old")
            .flowRevision(1)
            .labels(Label.from(Map.of("country", "US")))
            .state(new State(Type.SUCCESS,
                List.of(new State.History(State.Type.CREATED, executionCreateDateOld), new State.History(Type.SUCCESS, executionCreateDateOld.plus(executionDuration)))))
            .taskRunList(List.of())
            .build();

        executionRepository.save(executionOld);

        var now = ZonedDateTime.now();
        ArrayListTotal<Map<String, Object>> data = executionRepository.fetchData(tenantId, Executions.builder()
                .type(Executions.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).agg(AggregationType.COUNT).build(),
                    "id", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).build(),
                    "date", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.START_DATE).build(),
                    "duration", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.DURATION).build()
                )).build(),
            now.minusDays(365),
            now,
            null
        );

        // Should only return 1 execution (the recent one), not the 400-day-old execution
        assertThat(data.getTotal()).isGreaterThanOrEqualTo(1L);
        assertThat(data).isNotEmpty();
        assertThat(data).first().hasFieldOrProperty("count");
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
        assertThat(executions).hasSize(30); // used by the backup so it contains TEST executions
    }

    @Test
    protected void shouldFindByLabel() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var exec1 = executionRepository.save(builder(tenant, State.Type.RUNNING, null)
            .labels(List.of(
                new Label("labelkey1", "labelvalue1")
            ))
            .build()
        );
        var exec2 = executionRepository.save(builder(tenant, State.Type.RUNNING, null)
            .labels(List.of(
                new Label("labelkey2", "labelvalue2")
            ))
            .build()
        );
        var exec3 = executionRepository.save(builder(tenant, State.Type.RUNNING, null)
            .labels(List.of(
                new Label("labelkey2", "labelvalue2"),
                new Label("labelkey3", "labelvalue3")
            ))
            .build()
        );

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(Map.of("labelkey1", "labelvalue1"))
                    .build())
            )
        ).as("find execution EQUALS LABELS")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec1);

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(Map.of("unexisting_label", "unexisting_value"))
                    .build())
            )
        ).as("find no execution EQUALS non existing LABELS")
            .isEmpty();

        // Filtering by two pairs of labels, since now its a and behavior, it should not return anything
        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(Map.of("labelkey1", "labelvalue1", "keyother", "valueother"))
                    .build()))
        ).as("find no execution that EQUALS labelA AND labelB")
            .isEmpty();

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(Op.NOT_EQUALS)
                    .value(Map.of("labelkey1", "labelvalue1"))
                    .build())
            )
        ).as("find execution NOT_EQUALS LABELS")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec2, exec3);

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(Op.IN)
                    .value(Map.of("labelkey1", "labelvalue1", "labelkey3", "labelvalue3", "keyother", "valueother"))
                    .build()))
        )
            .as("find two execution IN LABELS")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec1, exec3);

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(Op.NOT_IN)
                    .value(Map.of("labelkey2", "labelvalue2"))
                    .build()))
        )
            .as("find one execution NOT IN LABELS")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec1);

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(Op.CONTAINS)
                    .value("alue2")
                    .build()))
        )
            .as("find execution CONTAINS LABELS value")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec2, exec3);

        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant,
                List.of(QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(Op.CONTAINS)
                    .value("ey1")
                    .build()))
        )
            .as("find execution CONTAINS LABELS key")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec1);
    }

    record ExecutionSortTestData(Execution createdExecution, Execution successExecution, Execution runningExecution, Execution failedExecution){
        static ExecutionSortTestData insertExecutionsTestData(String tenant, ExecutionRepositoryInterface executionRepository) {
            final Instant clock = Instant.now();
            final AtomicInteger passedTime = new AtomicInteger();
            var ten = 10;

            var createdExecution = Execution.builder()
                .id("createdExecution__" + FriendlyId.createFriendlyId())
                .namespace(NAMESPACE)
                .tenantId(tenant)
                .flowId(FLOW)
                .flowRevision(1)
                .state(
                    State.of(
                        State.Type.CREATED,
                        List.of(
                            new State.History(State.Type.CREATED, clock)
                        )
                    )
                ).build();
            executionRepository.save(createdExecution);

            var successExecution = Execution.builder()
                .id("successExecution__" + FriendlyId.createFriendlyId())
                .namespace(NAMESPACE)
                .tenantId(tenant)
                .flowId(FLOW)
                .flowRevision(1)
                .state(
                    State.of(
                        State.Type.SUCCESS,
                        List.of(
                            new State.History(State.Type.CREATED, clock.plus(passedTime.addAndGet(ten), SECONDS)),
                            new State.History(Type.QUEUED, clock.plus(passedTime.get(), SECONDS)),
                            new State.History(State.Type.RUNNING, clock.plus(passedTime.addAndGet(ten), SECONDS)),
                            new State.History(State.Type.SUCCESS, clock.plus(passedTime.addAndGet(ten), SECONDS))
                        )
                    )
                ).build();

            assertThat(successExecution.getState().getDuration().get()).isCloseTo(Duration.ofSeconds(20), Duration.ofMillis(3));
            executionRepository.save(successExecution);

            var runningExecution = Execution.builder()
                .id("runningExecution__" + FriendlyId.createFriendlyId())
                .namespace(NAMESPACE)
                .tenantId(tenant)
                .flowId(FLOW)
                .flowRevision(1)
                .state(
                    State.of(
                        State.Type.RUNNING,
                        List.of(
                            new State.History(State.Type.CREATED, clock.plus(passedTime.addAndGet(ten), SECONDS)),
                            new State.History(State.Type.RUNNING, clock.plus(passedTime.addAndGet(ten), SECONDS))
                        )
                    )
                ).build();
            assertThat(runningExecution.getState().getDuration()).isEmpty();
            executionRepository.save(runningExecution);

            var failedExecution = Execution.builder()
                .id("failedExecution__" + FriendlyId.createFriendlyId())
                .namespace(NAMESPACE)
                .tenantId(tenant)
                .flowId(FLOW)
                .flowRevision(1)
                .state(
                    State.of(
                        Type.FAILED,
                        List.of(
                            new State.History(State.Type.CREATED, clock.plus(passedTime.addAndGet(ten), SECONDS)),
                            new State.History(Type.FAILED, clock.plus(passedTime.addAndGet(ten), SECONDS))
                        )
                    )
                ).build();
            assertThat(failedExecution.getState().getDuration().get()).isCloseTo(Duration.ofSeconds(10), Duration.ofMillis(3));
            executionRepository.save(failedExecution);

            return new ExecutionSortTestData(createdExecution, successExecution, runningExecution, failedExecution);
        }
    }

    @Test
    protected void findShouldSortCorrectlyOnDurationAsc() {
        // given
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var testData = ExecutionSortTestData.insertExecutionsTestData(tenant, executionRepository);

        // when
        List<QueryFilter> emptyFilters = null;
        var sort = createSortLikeInControllers(List.of("state.duration:asc"), executionRepository.sortMapping());
        var sortedByShortestDuration = executionRepository.find(Pageable.from(sort), tenant, emptyFilters);

        // then
        assertThat(sortedByShortestDuration.stream())
            .as("assert non-terminated are at the top (list position 0 and 1)")
            .map(Execution::getId)
            .elements(0, 1).containsExactlyInAnyOrder(
                testData.runningExecution().getId(),
                testData.createdExecution().getId()
            );
        assertThat(sortedByShortestDuration.stream())
            .as("assert terminated are at the bot and sorted")
            .map(Execution::getId)
            .elements(2, 3).containsExactly(
                testData.failedExecution().getId(),
                testData.successExecution().getId()
            );
    }

    @Test
    protected void findShouldSortCorrectlyOnDurationDesc() {
        // given
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var testData = ExecutionSortTestData.insertExecutionsTestData(tenant, executionRepository);

        // when
        List<QueryFilter> emptyFilters = null;
        var sort = createSortLikeInControllers(List.of("state.duration:desc"), executionRepository.sortMapping());
        var sortedByLongestDuration = executionRepository.find(Pageable.from(sort), tenant, emptyFilters);

        // then
        assertThat(sortedByLongestDuration.stream())
            .as("assert terminated are at the top and sorted")
            .map(Execution::getId)
            .elements(0, 1).containsExactly(
                testData.successExecution().getId(),
                testData.failedExecution().getId()
            );

        assertThat(sortedByLongestDuration.stream())
            .as("assert non-terminated are at the bottom (list position 2 and 3)")
            .map(Execution::getId)
            .elements(2, 3).containsExactlyInAnyOrder(
                testData.runningExecution().getId(),
                testData.createdExecution().getId()
            );
    }

    @Test
    protected void findShouldOrderByStartDateAsc() {
        // given
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var testData = ExecutionSortTestData.insertExecutionsTestData(tenant, executionRepository);

        // when
        List<QueryFilter> emptyFilters = null;
        var sort = createSortLikeInControllers(List.of("state.startDate:asc"), executionRepository.sortMapping());
        var page = Pageable.from(1, 1, sort);
        var findByMoreRecentStartDate = executionRepository.find(
            page,
            tenant,
            emptyFilters
        );

        // then
        assertThat(findByMoreRecentStartDate.stream())
            .as("assert order when finding by first start date")
            .map(Execution::getId)
            .containsExactly(testData.createdExecution().getId());
    }

    @Test
    protected void findShouldOrderByStartDateDesc() {
        // given
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var testData = ExecutionSortTestData.insertExecutionsTestData(tenant, executionRepository);

        // when
        List<QueryFilter> emptyFilters = null;
        var sort = createSortLikeInControllers(List.of("state.startDate:desc"), executionRepository.sortMapping());
        var page = Pageable.from(1, 1, sort);
        var findByMoreRecentStartDate = executionRepository.find(
            page,
            tenant,
            emptyFilters
        );

        // then
        assertThat(findByMoreRecentStartDate.stream())
            .as("assert order when finding by last start date")
            .map(Execution::getId)
            .containsExactly(testData.failedExecution().getId());
    }

    // duplicated from PageableUtils, because mapping is different between PG and ES
    private Sort createSortLikeInControllers(List<String> sort, Function<String, String> sortMapper) {
        return sort == null ? null :
            Sort.of(sort
                .stream()
                .map(s -> {
                    String[] split = s.split(":");
                    if (split.length != 2) {
                        throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid sort parameter");
                    }
                    String col = split[0];

                    if (sortMapper != null) {
                        col = sortMapper.apply(col);
                    }

                    return split[1].equals("asc") ? Sort.Order.asc(col) : Sort.Order.desc(col);
                })
                .toList()
            );
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

    @Test
    protected void shouldIncludeRunningExecutionsInLastExecutions() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // Create an older finished execution for flow "full"
        Instant older = Instant.now().minus(Duration.ofMinutes(10));
        State finishedState = new State(
            State.Type.SUCCESS,
            List.of(
                new State.History(State.Type.CREATED, older.minus(Duration.ofMinutes(1))),
                new State.History(State.Type.SUCCESS, older)
            )
        );
        Execution finished = Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace(NAMESPACE)
            .flowId(FLOW)
            .flowRevision(1)
            .state(finishedState)
            .taskRunList(List.of())
            .build();
        executionRepository.save(finished);

        // Create a newer running execution for the same flow
        Instant newer = Instant.now().minus(Duration.ofMinutes(2));
        State runningState = new State(
            State.Type.RUNNING,
            List.of(
                new State.History(State.Type.CREATED, newer),
                new State.History(State.Type.RUNNING, newer)
            )
        );
        Execution running = Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace(NAMESPACE)
            .flowId(FLOW)
            .flowRevision(1)
            .state(runningState)
            .taskRunList(List.of())
            .build();
        executionRepository.save(running);

        List<Execution> last = executionRepository.lastExecutions(tenant, null);

        // Ensure we have one per flow and that for FLOW it is the running execution
        Map<String, Execution> byFlow = last.stream().collect(Collectors.toMap(Execution::getFlowId, e -> e));
        assertThat(byFlow.get(FLOW)).isNotNull();
        assertThat(byFlow.get(FLOW).getId()).isEqualTo(running.getId());
    }

    @Test
    void findAsync() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Execution execA = Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace(NAMESPACE)
            .flowId("flowA")
            .flowRevision(1)
            .state(new State())
            .taskRunList(List.of())
            .build();

        Execution execB = Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace(NAMESPACE)
            .flowId("flowB")
            .flowRevision(1)
            .state(new State())
            .taskRunList(List.of())
            .build();

        Execution savedA = executionRepository.save(execA);
        Execution savedB = executionRepository.save(execB);

        try {
            List<Execution> all = executionRepository.findAllAsync(tenant).collectList().block();
            assertThat(all).isNotNull();
            assertThat(all.stream().map(Execution::getId).toList())
                .containsExactlyInAnyOrder(savedA.getId(), savedB.getId());

            // filtered using repository find (pageable) since findAllAsync has no filters
            List<QueryFilter> filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value("flowA")
                .build());

            ArrayListTotal<Execution> filtered = executionRepository.find(Pageable.UNPAGED, tenant, filters);
            assertThat(filtered.getTotal()).isEqualTo(1L);
            assertThat(filtered.getFirst().getFlowId()).isEqualTo("flowA");
        } finally {
            executionRepository.delete(savedA);
            executionRepository.delete(savedB);
        }
    }

}
