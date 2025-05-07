package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class FlowTriggerCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void trigger() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> flowListener = new AtomicReference<>();
        AtomicReference<Execution> flowListenerNoInput = new AtomicReference<>();
        AtomicReference<Execution> flowListenerNamespace = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS) {
                if (flowListenerNoInput.get() == null && execution.getFlowId().equals("trigger-flow-listener-no-inputs")) {
                    flowListenerNoInput.set(execution);
                    countDownLatch.countDown();
                } else if (flowListener.get() == null && execution.getFlowId().equals("trigger-flow-listener")) {
                    flowListener.set(execution);
                    countDownLatch.countDown();
                } else if (flowListenerNamespace.get() == null && execution.getFlowId().equals("trigger-flow-listener-namespace-condition")) {
                    flowListenerNamespace.set(execution);
                    countDownLatch.countDown();
                }
            }
        });

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger", "trigger-flow");

        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertTrue(countDownLatch.await(15, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(flowListener.get().getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListener.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(flowListener.get().getTaskRunList().getFirst().getOutputs().get("value")).isEqualTo("childs: from parents: " + execution.getId());
        assertThat(flowListener.get().getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListener.get().getTrigger().getVariables().get("namespace")).isEqualTo("io.kestra.tests.trigger");
        assertThat(flowListener.get().getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");

        assertThat(flowListenerNoInput.get().getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("namespace")).isEqualTo("io.kestra.tests.trigger");
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");
        assertThat(flowListenerNoInput.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(flowListenerNamespace.get().getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNamespace.get().getTrigger().getVariables().get("namespace")).isEqualTo("io.kestra.tests.trigger");
        // it will be triggered for 'trigger-flow' or any of the 'trigger-flow-listener*', so we only assert that it's one of them
        assertThat(flowListenerNamespace.get().getTrigger().getVariables().get("flowId"))
            .satisfiesAnyOf(
                arg -> assertThat(arg).isEqualTo("trigger-flow"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener-no-inputs"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener")
            );
    }

    public void triggerWithPause() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        List<Execution> flowListeners = new ArrayList<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS && execution.getFlowId().equals("trigger-flow-listener-with-pause")) {
                flowListeners.add(execution);
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.pause", "trigger-flow-with-pause");

        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertTrue(countDownLatch.await(15, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(flowListeners.size()).isEqualTo(4);
        assertThat(flowListeners.get(0).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(flowListeners.get(1).getOutputs().get("status")).isEqualTo("PAUSED");
        assertThat(flowListeners.get(2).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(flowListeners.get(3).getOutputs().get("status")).isEqualTo("SUCCESS");
    }
}
