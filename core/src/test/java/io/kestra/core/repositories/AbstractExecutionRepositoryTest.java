package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
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
                    Return.builder().id("first").type(Return.class.getName()).format("test").build())
                )
                .withState(State.Type.SUCCESS),
            spyTaskRun(TaskRun.of(execution.build(), ResolvedTask.of(
                        Return.builder().id("second").type(Return.class.getName()).format("test").build())
                    )
                    .withState(state),
                state
            ),
            TaskRun.of(execution.build(), ResolvedTask.of(
                Return.builder().id("third").type(Return.class.getName()).format("test").build())).withState(state)
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
    }

    @Test
    protected void find() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, List.of(State.Type.RUNNING, State.Type.FAILED), null, null, null);
        assertThat(executions.getTotal(), is(8L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, Map.of("key", "value"), null, null);
        assertThat(executions.getTotal(), is(1L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, Map.of("key", "value2"), null, null);
        assertThat(executions.getTotal(), is(0L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, "second", null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(13L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, NAMESPACE, "second", null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(13L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, "io.kestra", null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(28L));
    }

    @Test
    protected void findTriggerExecutionId() {
        String executionTriggerId = IdUtils.create();

        inject(executionTriggerId);
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10), null, null, null, null, null, null, null, null, null, executionTriggerId, null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId"), is(executionTriggerId));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, null, null, ExecutionRepositoryInterface.ChildFilter.CHILD);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));
        assertThat(executions.getFirst().getTrigger().getVariables().get("executionId"), is(executionTriggerId));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, null, null, ExecutionRepositoryInterface.ChildFilter.MAIN);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));
        assertThat(executions.getFirst().getTrigger(), is(nullValue()));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(56L));
    }

    @Test
    protected void findWithSort() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))),  null, null, null, null, null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, List.of(State.Type.RUNNING, State.Type.FAILED), null, null, null);
        assertThat(executions.getTotal(), is(8L));
    }

    @Test
    protected void findTaskRun() {
        inject();

        ArrayListTotal<TaskRun> taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, null, null, null, null, null, null, null, null, null);
        assertThat(taskRuns.getTotal(), is(71L));
        assertThat(taskRuns.size(), is(10));

        taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, null, null, null, null, null, null, Map.of("key", "value"), null, null);
        assertThat(taskRuns.getTotal(), is(1L));
        assertThat(taskRuns.size(), is(1));
    }


    @Test
    protected void findById() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getId(), is(ExecutionFixture.EXECUTION_1.getId()));
        });
    }

    @Test
    protected void purge() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(true));

        executionRepository.purge(ExecutionFixture.EXECUTION_1);

        full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(false));
    }

    @Test
    protected void delete() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(true));

        executionRepository.delete(ExecutionFixture.EXECUTION_1);

        full = executionRepository.findById(null, ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(false));
    }

    @Test
    protected void mappingConflict() {
        executionRepository.save(ExecutionFixture.EXECUTION_2);
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(null, NAMESPACE, FLOW, Pageable.from(1, 10));

        assertThat(page1.size(), is(2));
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

        assertThat(result.size(), is(1));
        assertThat(result.get("io.kestra.unittest").size(), is(2));

        DailyExecutionStatistics full = result.get("io.kestra.unittest").get(FLOW).get(10);
        DailyExecutionStatistics second = result.get("io.kestra.unittest").get("second").get(10);

        assertThat(full.getDuration().getAvg().toMillis(), greaterThan(0L));
        assertThat(full.getExecutionCounts().size(), is(11));
        assertThat(full.getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS), is(7L));
        assertThat(full.getExecutionCounts().get(State.Type.CREATED), is(0L));

        assertThat(second.getDuration().getAvg().toMillis(), greaterThan(0L));
        assertThat(second.getExecutionCounts().size(), is(11));
        assertThat(second.getExecutionCounts().get(State.Type.SUCCESS), is(13L));
        assertThat(second.getExecutionCounts().get(State.Type.CREATED), is(0L));

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

        assertThat(result.size(), is(1));
        assertThat(result.get("io.kestra.unittest").size(), is(1));
        full = result.get("io.kestra.unittest").get("*").get(10);
        assertThat(full.getDuration().getAvg().toMillis(), greaterThan(0L));
        assertThat(full.getExecutionCounts().size(), is(11));
        assertThat(full.getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS), is(20L));
        assertThat(full.getExecutionCounts().get(State.Type.CREATED), is(0L));

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

        assertThat(result.size(), is(1));
        assertThat(result.get("io.kestra.unittest").size(), is(1));
        assertThat(result.get("io.kestra.unittest").get(FLOW).size(), is(11));
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

        assertThat(result.size(), is(2));
        assertThat(result, containsInAnyOrder(
            allOf(
                hasProperty("state", hasProperty("current", is(State.Type.FAILED))),
                hasProperty("namespace", is(NAMESPACE))
            ),
            allOf(
                hasProperty("state", hasProperty("current", is(State.Type.SUCCESS))),
                hasProperty("namespace", is(anotherNamespace))
            )
        ));
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

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(11));
        assertThat(result.get(10).getDuration().getAvg().toMillis(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(21L));

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

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(21L));

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
        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(20L));

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
        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(1L));
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

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(11));
        assertThat(result.get(10).getDuration().getAvg().toMillis(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(57L));

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

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(57L));

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
        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(55L));

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
        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(2L));
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
        assertThat(result.size(), is(4));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount(), is(2L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount(), is(3L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount(), is(9L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("missing")).findFirst().get().getCount(), is(0L));

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
        assertThat(result.size(), is(3));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount(), is(2L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount(), is(3L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount(), is(9L));

        result = executionRepository.executionCounts(
            null,
            null,
            null,
            null,
            null,
            List.of(NAMESPACE)
        );
        assertThat(result.size(), is(1));
        assertThat(result.stream().filter(executionCount -> executionCount.getNamespace().equals(NAMESPACE)).findFirst().get().getCount(), is(14L));
    }

    @Test
    protected void update() {
        Execution execution = ExecutionFixture.EXECUTION_1;
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Label label = new Label("key", "value");
        Execution updated = execution.toBuilder().labels(List.of(label)).build();
        executionRepository.update(updated);

        Optional<Execution> validation = executionRepository.findById(null, updated.getId());
        assertThat(validation.isPresent(), is(true));
        assertThat(validation.get().getLabels().size(), is(1));
        assertThat(validation.get().getLabels().getFirst(), is(label));
    }

    @Test
    void shouldFindLatestExecutionGivenState() {
        Execution earliest = buildWithCreatedDate(Instant.now().minus(Duration.ofMinutes(10)));
        Execution latest = buildWithCreatedDate(Instant.now().minus(Duration.ofMinutes(5)));

        executionRepository.save(earliest);
        executionRepository.save(latest);

        Optional<Execution> result = executionRepository.findLatestForStates(null, "io.kestra.unittest", "full", List.of(State.Type.CREATED));
        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getId(), is(latest.getId()));
    }

    @Test
    void fetchData() throws IOException {
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

        List<Map<String, Object>> data = executionRepository.fetchData(tenantId, Executions.builder()
                .type(Executions.class.getName())
                .columns(Map.of(
                    "count", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.ID).agg(AggregationType.COUNT).build(),
                    "country", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.LABELS).labelKey("country").build(),
                    "date", ColumnDescriptor.<Executions.Fields>builder().field(Executions.Fields.START_DATE).build()
                )).build(),
            ZonedDateTime.now().minus(1, ChronoUnit.DAYS),
            ZonedDateTime.now()
        );

        assertThat(data.size(), is(1));
        assertThat(data.get(0).get("count"), is(1L));
        assertThat(data.get(0).get("country"), is("FR"));
        assertThat(data.get(0).get("date"), is(execution.getState().getStartDate()));
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
}
