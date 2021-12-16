package io.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.tasks.debugs.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest
public class ElasticSearchExecutionRepositoryTest {
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    ElasticSearchExecutionRepository executionRepository;

    @Inject
    ElasticSearchRepositoryTestUtils utils;

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


    void inject() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }
    }

    @Test
    void find() {
        inject();

        ArrayListTotal<Execution> executions = executionRepository.find("*", Pageable.from(1, 10), null);
        assertThat(executions.getTotal(), is(28L));
        assertThat(executions.size(), is(10));
    }

    @Test
    void findTaskRun() {
        inject();

        ArrayListTotal<TaskRun> executions = executionRepository.findTaskRun("*", Pageable.from(1, 10), null);
        assertThat(executions.getTotal(), is(71L));
        assertThat(executions.size(), is(10));
    }


    @Test
    void findById() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getId(), is(ExecutionFixture.EXECUTION_1.getId()));
        });
    }

    @Test
    void mappingConflict() {
        executionRepository.save(ExecutionFixture.EXECUTION_2);
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(NAMESPACE, FLOW, Pageable.from(1, 10));

        assertThat(page1.size(), is(2));
    }

    @Test
    void dailyGroupByFlowStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        Map<String, Map<String, List<DailyExecutionStatistics>>> result = executionRepository.dailyGroupByFlowStatistics(
            "*",
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            false
        );

        assertThat(result.size(), is(1));
        assertThat(result.get("io.kestra.unittest").size(), is(2));

        DailyExecutionStatistics full = result.get("io.kestra.unittest").get(FLOW).get(10);
        DailyExecutionStatistics second = result.get("io.kestra.unittest").get("second").get(10);

        assertThat(full.getDuration().getAvg().getSeconds(), greaterThan(0L));
        assertThat(full.getExecutionCounts().size(), is(8));
        assertThat(full.getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS), is(7L));
        assertThat(full.getExecutionCounts().get(State.Type.CREATED), is(0L));

        assertThat(second.getDuration().getAvg().getSeconds(), greaterThan(0L));
        assertThat(second.getExecutionCounts().size(), is(8));
        assertThat(second.getExecutionCounts().get(State.Type.SUCCESS), is(13L));
        assertThat(second.getExecutionCounts().get(State.Type.CREATED), is(0L));

        result = executionRepository.dailyGroupByFlowStatistics(
            "*",
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            true
        );

        assertThat(result.size(), is(1));
        assertThat(result.get("io.kestra.unittest").size(), is(1));
        full = result.get("io.kestra.unittest").get("*").get(10);
        assertThat(full.getDuration().getAvg().getSeconds(), greaterThan(0L));
        assertThat(full.getExecutionCounts().size(), is(8));
        assertThat(full.getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(full.getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(full.getExecutionCounts().get(State.Type.SUCCESS), is(20L));
        assertThat(full.getExecutionCounts().get(State.Type.CREATED), is(0L));
    }

    @Test
    void dailyStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            "*",
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            false
        );

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(8));
        assertThat(result.get(10).getDuration().getAvg().getSeconds(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(20L));
    }


    @Test
    void taskRunsDailyStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics(
            "*",
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now(),
            true
        );

        assertThat(result.size(), is(11));
        assertThat(result.get(10).getExecutionCounts().size(), is(8));
        assertThat(result.get(10).getDuration().getAvg().getSeconds(), greaterThan(0L));

        assertThat(result.get(10).getExecutionCounts().get(State.Type.FAILED), is(3L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.RUNNING), is(5L * 2));
        assertThat(result.get(10).getExecutionCounts().get(State.Type.SUCCESS), is(55L));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void executionsCount() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                State.Type.SUCCESS,
                i < 4 ? "first" : (i < 10 ? "second" : "third")
            ).build());
        }

        List<ExecutionCount> result = executionRepository.executionCounts(
            List.of(
                new Flow(NAMESPACE, "first"),
                new Flow(NAMESPACE, "second"),
                new Flow(NAMESPACE, "third"),
                new Flow(NAMESPACE, "missing")
            ),
            "*",
            ZonedDateTime.now().minusDays(10),
            ZonedDateTime.now()
        );

        assertThat(result.size(), is(4));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("first")).findFirst().get().getCount(), is(4L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("second")).findFirst().get().getCount(), is(6L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("third")).findFirst().get().getCount(), is(18L));
        assertThat(result.stream().filter(executionCount -> executionCount.getFlowId().equals("missing")).findFirst().get().getCount(), is(0L));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        executionRepository.initMapping();
    }
}
