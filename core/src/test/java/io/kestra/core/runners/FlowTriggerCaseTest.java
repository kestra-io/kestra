package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Singleton
public class FlowTriggerCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logEntryQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void trigger() throws InterruptedException, TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicReference<Execution> flowListener = new AtomicReference<>();
        AtomicReference<Execution> flowListenerNoInput = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS) {
                if (flowListenerNoInput.get() == null && execution.getFlowId().equals("trigger-flow-listener-no-inputs")) {
                    flowListenerNoInput.set(execution);
                    countDownLatch.countDown();
                } else if (flowListener.get() == null && execution.getFlowId().equals("trigger-flow-listener")) {
                    flowListener.set(execution);
                    countDownLatch.countDown();
                }
            }
        });

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-flow");

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        countDownLatch.await(10, TimeUnit.SECONDS);

        assertThat(flowListener.get().getTaskRunList().size(), is(1));
        assertThat(flowListener.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(flowListener.get().getTaskRunList().get(0).getOutputs().get("value"), is("childs: from parents: " + execution.getId()));
        assertThat(flowListener.get().getTrigger().getVariables().get("executionId"), is(execution.getId()));
        assertThat(flowListener.get().getTrigger().getVariables().get("namespace"), is("io.kestra.tests.trigger"));
        assertThat(flowListener.get().getTrigger().getVariables().get("flowId"), is("trigger-flow"));

        assertThat(flowListenerNoInput.get().getTaskRunList().size(), is(1));
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("executionId"), is(execution.getId()));
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("namespace"), is("io.kestra.tests.trigger"));
        assertThat(flowListenerNoInput.get().getTrigger().getVariables().get("flowId"), is("trigger-flow"));

        assertThat(flowListenerNoInput.get().getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
