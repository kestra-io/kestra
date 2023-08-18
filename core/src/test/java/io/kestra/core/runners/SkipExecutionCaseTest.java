package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Singleton
public class SkipExecutionCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private SkipExecutionService skipExecutionService;

    public void skipExecution() throws TimeoutException, InterruptedException {
        Flow flow = createFlow();
        Execution execution1 = runnerUtils.newExecution(flow, null, null);
        String execution1Id = execution1.getId();
        skipExecutionService.setSkipExecutions(List.of(execution1Id));

        executionQueue.emit(execution1);
        Execution execution2 = runnerUtils.runOne("io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent(), is(State.Type.SUCCESS));
        Thread.sleep(25); // to be 100% sure that it works, add a slight delay to be sure we didn't miss the execution by chance
        execution1 = executionRepository.findById(execution1Id).get();
        assertThat(execution1.getState().getCurrent(), is(State.Type.CREATED));
    }

    private Flow createFlow() {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("{{ inputs.testInputs }}")
                .build()))
            .build();
    }
}
