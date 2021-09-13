package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.services.ExecutionService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class PauseTest extends AbstractMemoryRunnerTest {
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Test
    void run() throws Exception {
        Flow flow = flowRepository.findById("io.kestra.tests", "pause").orElseThrow();
        Execution execution = runnerUtils.runOne("io.kestra.tests", "pause", Duration.ofSeconds(120));

        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.PAUSED));
        assertThat(execution.getTaskRunList(), hasSize(1));

        Execution restarted = executionService.markAs(
            execution,
            execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
            State.Type.RUNNING
        );

        execution = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.SUCCESS,
            () -> executionQueue.emit(restarted),
            Duration.ofSeconds(120)
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}