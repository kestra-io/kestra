package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.tasks.debugs.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest(transactional = false)
public abstract class AbstractExecutionRepositoryTest {
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    public static Execution.ExecutionBuilder builder(State.Type state, String flowId) {
        State finalState = randomDuration(state);

        Execution.ExecutionBuilder execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(NAMESPACE)
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
            return execution.taskRunList(List.of(taskRuns.get(0), taskRuns.get(1), taskRuns.get(2)));
        }

        return execution.taskRunList(List.of(taskRuns.get(0), taskRuns.get(1)));
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
        executionRepository.save(builder(State.Type.RUNNING, null).labels(List.of(new Label("key", "value"))).build());
        for (int i = 1; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }
    }

    @Test
    protected void find() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, List.of(State.Type.RUNNING, State.Type.FAILED), null);
        assertThat(executions.getTotal(), is(8L));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, null, Map.of("key", "value"));
        assertThat(executions.getTotal(), is(1L));
    }

    @Test
    protected void findWithSort() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))),  null, null, null, null, null, null, null, null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));

        executions = executionRepository.find(Pageable.from(1, 10),  null, null, null, null, null, null, List.of(State.Type.RUNNING, State.Type.FAILED), null);
        assertThat(executions.getTotal(), is(8L));
    }

    @Test
    protected void findTaskRun() {
        inject();

        ArrayListTotal<TaskRun> taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, null, null, null, null, null, null, null);
        assertThat(taskRuns.getTotal(), is(71L));
        assertThat(taskRuns.size(), is(10));

        taskRuns = executionRepository.findTaskRun(Pageable.from(1, 10), null, null, null, null, null, null, null, Map.of("key", "value"));
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
    protected void dailyStatistics() throws InterruptedException {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        // mysql need some time ...
        Thread.sleep(500);

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            false
        );

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(11));
        assertThat(result.get(10).getDuration().getAvg().toMillis(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(20L));
    }

    @Test
    protected void taskRunsDailyStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            null,
            null,
            null,
            null,
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            null,
            true
        );

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(11));
        assertThat(result.get(10).getDuration().getAvg().toMillis(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(55L));
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
            ZonedDateTime.now()
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
            null
        );
        assertThat(result.size(), is(3));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount(), is(2L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount(), is(3L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount(), is(9L));
    }
}
