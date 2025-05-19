package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
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
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@KestraTest
public abstract class AbstractExecutionRepositoryTest {
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    public static Execution.ExecutionBuilder builder(State.Type state, String flowId) {
        return builder(state, flowId, NAMESPACE);
    }

    public static Execution.ExecutionBuilder builder(State.Type state, String flowId, String namespace) {
        State finalState = randomDuration(state);

        Execution.ExecutionBuilder execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(namespace)
            .flowId(flowId == null ? FLOW : flowId)
            .flowRevision(1)
            .state(finalState);


        List<TaskRun> taskRuns = Arrays.asList(
            TaskRun.of(execution.build(), ResolvedTask.of(
                    Return.builder().id("first").type(Return.class.getName()).format(Property.of("test")).build())
                )
                .withState(State.Type.SUCCESS),
            spyTaskRun(TaskRun.of(execution.build(), ResolvedTask.of(
                        Return.builder().id("second").type(Return.class.getName()).format(Property.of("test")).build())
                    )
                    .withState(state),
                state
            ),
            TaskRun.of(execution.build(), ResolvedTask.of(
                Return.builder().id("third").type(Return.class.getName()).format(Property.of("test")).build())).withState(state)
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

    protected void inject() {
        inject(null);
    }

    protected void inject(String executionTriggerId) {
        ExecutionTrigger executionTrigger = null;

        if (executionTriggerId != null) {
            executionTrigger = ExecutionTrigger.builder()
                .variables(Map.of("executionId", executionTriggerId))
                .build();
        }

        executionRepository.save(builder(State.Type.RUNNING, null)
            .labels(List.of(
                new Label("key", "value"),
                new Label("key2", "value2")
            ))
            .trigger(executionTrigger)
            .build()
        );
        for (int i = 1; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).trigger(executionTrigger).build());
        }

        // add a test execution, this should be ignored in search & statistics
        executionRepository.save(builder(
            State.Type.SUCCESS,
            null
        )
            .trigger(executionTrigger)
            .kind(ExecutionKind.TEST)
            .build());
    }

    @Test
    protected void find() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10),  null, null);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);

        List<QueryFilter> filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.EQUALS)
                .value( List.of(State.Type.RUNNING, State.Type.FAILED))
                .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(8L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value"))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(1L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value2"))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(0L);

        filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.EQUALS)
                .value(Map.of("key", "value", "keyTest", "valueTest"))
                .build()
        );
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(1L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.FLOW_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value("second")
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
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
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(13L);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.NAMESPACE)
            .operation(QueryFilter.Op.STARTS_WITH)
            .value("io.kestra")
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
    }

    @Test
    protected void findTriggerExecutionId() {
        String executionTriggerId = IdUtils.create();

        inject(executionTriggerId);
        inject();

        var filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.TRIGGER_EXECUTION_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value(executionTriggerId)
            .build());
        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10), null, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);
        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.CHILD)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId")).isEqualTo(executionTriggerId);

        filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.CHILD_FILTER)
            .operation(QueryFilter.Op.EQUALS)
            .value(ExecutionRepositoryInterface.ChildFilter.MAIN)
            .build());

        executions = executionRepository.find(Pageable.from(1, 10),  null, filters );
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);
        assertThat(executions.getFirst().getTrigger()).isNull();

        executions = executionRepository.find(Pageable.from(1, 10),  null,null);
        assertThat(executions.getTotal()).isEqualTo(56L);
    }

    @Test
    protected void findWithSort() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))),  null, null);
        assertThat(executions.getTotal()).isEqualTo(28L);
        assertThat(executions.size()).isEqualTo(10);

        var filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.STATE)
            .operation(QueryFilter.Op.EQUALS)
            .value(List.of(State.Type.RUNNING, State.Type.FAILED))
            .build());
        executions = executionRepository.find(Pageable.from(1, 10),  null, filters);
        assertThat(executions.getTotal()).isEqualTo(8L);
    }

    @Test
    protected void findTaskRun() {
        inject();

        ArrayListTotal<TaskRun> taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, null);
        assertThat(taskRuns.getTotal()).isEqualTo(74L);
        assertThat(taskRuns.size()).isEqualTo(10);

        var filters = List.of(QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value(Map.of("key", "value"))
            .build());

        taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, filters);
        assertThat(taskRuns.getTotal()).isEqualTo(1L);
        assertThat(taskRuns.size()).isEqualTo(1);
    }


    @Test
    protected void findById() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent()).isTrue();

        full.ifPresent(current -> {
            assertThat(full.get().getId()).isEqualTo(ExecutionFixture.EXECUTION_1.getId());
        });
    }

    @Test
    protected void shouldFindByIdTestExecution() {
        executionRepository.save(ExecutionFixture.EXECUTION_TEST);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_TEST.getId());
        assertThat(full.isPresent()).isTrue();

        full.ifPresent(current -> {
            assertThat(full.get().getId()).isEqualTo(ExecutionFixture.EXECUTION_TEST.getId());
        });
    }

    @Test
    protected void purge() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent()).isTrue();

        executionRepository.purge(ExecutionFixture.EXECUTION_1);

        full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent()).isFalse();
    }

    @Test
    protected void delete() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent()).isTrue();

        executionRepository.delete(ExecutionFixture.EXECUTION_1);

        full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent()).isFalse();
    }

    @Test
    protected void mappingConflict() {
        executionRepository.save(ExecutionFixture.EXECUTION_2);
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(null, NAMESPACE, FLOW, Pageable.from(1, 10));

        assertThat(page1.size()).isEqualTo(2);
    }

    @Test
    protected void dailyGroupByFlowStatistics() throws InterruptedException {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        // mysql need some time ...
        Thread.sleep(500);

        Map<String, Map<String, List<DailyExecutionStatistics>>> result = executionRepository.dailyGroupByFlowStatistics(
            null,
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            false
        );

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("io.kestra.unittest").size()).isEqualTo(2);

        DailyExecutionStatistics full = result.get("io.kestra.unittest").get(FLOW).get(10);
        DailyExecutionStatistics second = result.get("io.kestra.unittest").get("second").get(10);

        assertThat(full.getDuration().getAvg().toMillis()).isGreaterThan(0L);
        assertThat(full.getExecutionCounts().size()).isEqualTo(11);
        assertThat(full.getExecutionCounts().get(State.Type.FAILED)).isEqualTo(3L);
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING)).isEqualTo(5L);
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(7L);
        assertThat(full.getExecutionCounts().get(State.Type.CREATED)).isEqualTo(0L);

        assertThat(second.getDuration().getAvg().toMillis()).isGreaterThan(0L);
        assertThat(second.getExecutionCounts().size()).isEqualTo(11);
        assertThat(second.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(13L);
        assertThat(second.getExecutionCounts().get(State.Type.CREATED)).isEqualTo(0L);

        result = executionRepository.dailyGroupByFlowStatistics(
            null,
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            true
        );

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("io.kestra.unittest").size()).isEqualTo(1);
        full = result.get("io.kestra.unittest").get("*").get(10);
        assertThat(full.getDuration().getAvg().toMillis()).isGreaterThan(0L);
        assertThat(full.getExecutionCounts().size()).isEqualTo(11);
        assertThat(full.getExecutionCounts().get(State.Type.FAILED)).isEqualTo(3L);
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING)).isEqualTo(5L);
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(20L);
        assertThat(full.getExecutionCounts().get(State.Type.CREATED)).isEqualTo(0L);

        result = executionRepository.dailyGroupByFlowStatistics(
            null,
            null,
            null,
            null,
            List.of(ExecutionRepositoryInterface.FlowFilter.builder().namespace("io.kestra.unittest").id(FLOW).build()),
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            false
        );

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("io.kestra.unittest").size()).isEqualTo(1);
        assertThat(result.get("io.kestra.unittest").get(FLOW).size()).isEqualTo(11);
    }

    @Test
    protected void lastExecutions() throws InterruptedException {

        Instant executionNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Execution executionOld = builder(State.Type.SUCCESS, FLOW)
            .state(State.of(
                State.Type.SUCCESS,
                List.of(new State.History(
                    State.Type.SUCCESS,
                    executionNow.minus(1, ChronoUnit.DAYS)
                )))
            ).build();

        Execution executionFailed = builder(State.Type.FAILED, FLOW)
            .state(State.of(
                State.Type.FAILED,
                List.of(new State.History(
                    State.Type.FAILED,
                    executionNow.minus(1, ChronoUnit.HOURS)
                )))
            ).build();

        Execution executionRunning = builder(State.Type.RUNNING, FLOW)
            .state(State.of(
                State.Type.RUNNING,
                List.of(new State.History(State.Type.RUNNING, executionNow)))
            ).build();

        String anotherNamespace = "another";
        Execution executionSuccessAnotherNamespace = builder(State.Type.SUCCESS, FLOW, anotherNamespace)
            .state(State.of(
                State.Type.SUCCESS,
                List.of(new State.History(
                    State.Type.SUCCESS,
                    executionNow.minus(30, ChronoUnit.MINUTES)
                )))
            ).build();

        executionRepository.save(executionOld);
        executionRepository.save(executionFailed);
        executionRepository.save(executionRunning);

        executionRepository.save(executionSuccessAnotherNamespace);

        // mysql need some time ...
        Thread.sleep(500);

        List<Execution> result = executionRepository.lastExecutions(
                null,
                List.of(
                    ExecutionRepositoryInterface.FlowFilter.builder()
                        .id(FLOW)
                        .namespace(NAMESPACE).build(),
                    ExecutionRepositoryInterface.FlowFilter.builder()
                        .id(FLOW)
                        .namespace(anotherNamespace).build()
                )
        );

        assertThat(result.size()).isEqualTo(2);
        assertThat(result)
            .extracting(
                r -> r.getState().getCurrent(),
                Execution::getNamespace
            )
            .containsExactlyInAnyOrder(
                tuple(State.Type.FAILED, NAMESPACE),
                tuple(State.Type.SUCCESS, anotherNamespace)
            );
    }

    @Test
    protected void dailyStatistics() throws InterruptedException {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        executionRepository.save(builder(
            State.Type.SUCCESS,
            "second"
        ).namespace(NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE).build());

        // mysql need some time ...
        Thread.sleep(500);

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            null,
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            false);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().size()).isEqualTo(11);
        assertThat(result.get(10).getDuration().getAvg().toMillis()).isGreaterThan(0L);

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED)).isEqualTo(3L);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING)).isEqualTo(5L);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(21L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.USER, FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            false);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(21L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.USER),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            false);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(20L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            false);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @Test
    protected void taskRunsDailyStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        executionRepository.save(builder(
            State.Type.SUCCESS,
            "second"
        ).namespace(NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE).build());

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            null,
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            true);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().size()).isEqualTo(11);
        assertThat(result.get(10).getDuration().getAvg().toMillis()).isGreaterThan(0L);

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED)).isEqualTo(3L * 2);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING)).isEqualTo(5L * 2);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(57L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.USER, FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            true);

        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(57L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.USER),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            true);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(55L);

        result = executionRepository.dailyStatistics(
            null,
            null,
            List.of(FlowScope.SYSTEM),
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            null,
            true);
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(2L);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    protected void executionsCount() throws InterruptedException {
        for (int i = 0; i < 14; i++) {
            executionRepository.save(builder(
                State.Type.SUCCESS,
                i < 2 ? "first" : (i < 5 ? "second" : "third")
            ).build());
        }

        // mysql need some time ...
        Thread.sleep(500);

        List<ExecutionCount> result = executionRepository.executionCounts(
            null,
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
            null,
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
            null,
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
        Execution execution = ExecutionFixture.EXECUTION_1;
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Label label = new Label("key", "value");
        Execution updated = execution.toBuilder().labels(List.of(label)).build();
        executionRepository.update(updated);

        Optional<Execution> validation = executionRepository.findById(null, updated.getId());
        assertThat(validation.isPresent()).isTrue();
        assertThat(validation.get().getLabels().size()).isEqualTo(1);
        assertThat(validation.get().getLabels().getFirst()).isEqualTo(label);
    }

    @Test
    void shouldFindLatestExecutionGivenState() {
        Execution earliest = buildWithCreatedDate(Instant.now().minus(Duration.ofMinutes(10)));
        Execution latest = buildWithCreatedDate(Instant.now().minus(Duration.ofMinutes(5)));

        executionRepository.save(earliest);
        executionRepository.save(latest);

        Optional<Execution> result = executionRepository.findLatestForStates(null, "io.kestra.unittest", "full", List.of(State.Type.CREATED));
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

    private static Execution buildWithCreatedDate(Instant instant) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("full")
            .flowRevision(1)
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, instant))))
            .inputs(ImmutableMap.of("test", "value"))
            .taskRunList(List.of())
            .build();
    }

    @Test
    protected void findAllAsync() {
        inject();

        List<Execution> executions = executionRepository.findAllAsync(null).collectList().block();
        assertThat(executions).hasSize(29); // used by the backup so it contains TEST executions
    }
}
