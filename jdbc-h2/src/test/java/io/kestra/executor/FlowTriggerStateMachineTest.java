package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class FlowTriggerStateMachineTest {
    private static Flow.FlowBuilder<?, ?> flowBuilder(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()));
    }

    @Test
    void shouldCreateListeningExecutionWhenUpstreamSucceeds(ExecutorStateMachineHarness harness) {
        Flow upstream = flowBuilder(harness.tenantId()).build();
        Flow listening = flowBuilder(harness.tenantId())
            .triggers(List.of(io.kestra.plugin.core.trigger.Flow.builder()
                .id("on-upstream")
                .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                .build()))
            .build();
        harness.registerFlow(listening);

        // Run the upstream flow to SUCCESS.
        Execution running = harness.process(upstream, Execution.newExecution(upstream, Collections.emptyList()));
        TaskRun taskRun = running.getTaskRunList().getFirst();
        Execution success = harness.process(
            upstream,
            running,
            WorkerTaskResult.builder().taskRun(taskRun.withState(State.Type.SUCCESS)).build()
        );
        assertThat(success.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // The flow trigger fired: a CREATED execution of the listening flow was emitted.
        assertThat(harness.emitted(Execution.class))
            .anySatisfy(created -> {
                assertThat(created.getFlowId()).isEqualTo(listening.getId());
                assertThat(created.getState().getCurrent()).isEqualTo(State.Type.CREATED);
            });
    }
}
