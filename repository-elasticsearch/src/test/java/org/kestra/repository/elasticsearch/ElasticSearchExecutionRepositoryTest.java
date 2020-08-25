package org.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.ArrayListTotal;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest
class ElasticSearchExecutionRepositoryTest {
    public static final String NAMESPACE = "org.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    ElasticSearchExecutionRepository executionRepository;

    @Inject
    ElasticSearchRepositoryTestUtils utils;

    private static Execution.ExecutionBuilder builder(State.Type state, String flowId) {
        State finalState = new State();

        finalState = spy(finalState
            .withState(state != null ? state : State.Type.SUCCESS));

        Random rand = new Random();
        doReturn(Duration.ofSeconds(rand.nextInt(150)))
            .when(finalState)
            .getDuration();

        return Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(NAMESPACE)
            .flowId(flowId == null ? FLOW : flowId)
            .flowRevision(1)
            .state(finalState);
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

        Map<String, Map<String, List<DailyExecutionStatistics>>> result = executionRepository.dailyGroupByFlowStatistics("state.startDate:[now-1d TO *] AND *");

        assertThat(result.size(), is(1));
        assertThat(result.get("org.kestra.unittest").size(), is(2));

        DailyExecutionStatistics full = result.get("org.kestra.unittest").get(FLOW).get(0);
        DailyExecutionStatistics second = result.get("org.kestra.unittest").get("second").get(0);

        assertThat(full.getDuration().getAvg().getSeconds(), greaterThan(0L));
        assertThat(full.getExecutionCounts().size(), is(3));
        assertThat(full.getExecutionCounts().stream().filter(f -> f.getState() == State.Type.FAILED).findFirst().orElseThrow().getCount(), is(3L));
        assertThat(full.getExecutionCounts().stream().filter(f -> f.getState() == State.Type.RUNNING).findFirst().orElseThrow().getCount(), is(5L));
        assertThat(full.getExecutionCounts().stream().filter(f -> f.getState() == State.Type.SUCCESS).findFirst().orElseThrow().getCount(), is(7L));

        assertThat(second.getDuration().getAvg().getSeconds(), greaterThan(0L));
        assertThat(second.getExecutionCounts().size(), is(1));
        assertThat(second.getExecutionCounts().stream().filter(f -> f.getState() == State.Type.SUCCESS).findFirst().orElseThrow().getCount(), is(13L));
    }


    @Test
    void dailyStatistics() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 15 ? null : "second"
            ).build());
        }

        List<DailyExecutionStatistics> result = executionRepository.dailyStatistics("state.startDate:[now-1d TO *] AND *");

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getExecutionCounts().size(), is(3));
        assertThat(result.get(0).getDuration().getAvg().getSeconds(), greaterThan(0L));

        assertThat(result.get(0).getExecutionCounts().stream().filter(f -> f.getState() == State.Type.FAILED).findFirst().orElseThrow().getCount(), is(3L));
        assertThat(result.get(0).getExecutionCounts().stream().filter(f -> f.getState() == State.Type.RUNNING).findFirst().orElseThrow().getCount(), is(5L));
        assertThat(result.get(0).getExecutionCounts().stream().filter(f -> f.getState() == State.Type.SUCCESS).findFirst().orElseThrow().getCount(), is(20L));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        executionRepository.initMapping();
    }
}
