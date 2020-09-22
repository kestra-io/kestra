package org.kestra.core.runners;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Singleton
public class FlowTriggerCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void trigger() throws InterruptedException, TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> flowListener = new AtomicReference<>();
        AtomicReference<Execution> flowListenerNoInput = new AtomicReference<>();
        AtomicReference<Execution> flowListenerFailed = new AtomicReference<>();

        executionQueue.receive(execution -> {
            if (execution.getState().getCurrent() == State.Type.SUCCESS || execution.getState().getCurrent() == State.Type.FAILED) {
                if (flowListenerNoInput.get() == null && execution.getFlowId().equals("trigger-flow-listener-no-inputs")) {
                    flowListenerNoInput.set(execution);
                    countDownLatch.countDown();
                } else if (flowListener.get() == null && execution.getFlowId().equals("trigger-flow-listener")) {
                    flowListener.set(execution);
                    countDownLatch.countDown();
                } else if (flowListenerFailed.get() == null && execution.getFlowId().equals("trigger-flow-listener-invalid")) {
                    flowListenerFailed.set(execution);
                    countDownLatch.countDown();
                }
            }
        });

        Execution execution = runnerUtils.runOne("org.kestra.tests", "trigger-flow");

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        countDownLatch.await();

        assertThat(flowListener.get().getTaskRunList().size(), is(1));
        assertThat(flowListener.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(flowListener.get().getTaskRunList().get(0).getOutputs().get("value"), is("childs: from parents: " + execution.getId()));

        assertThat(flowListenerNoInput.get().getTaskRunList().size(), is(1));
        assertThat(flowListenerNoInput.get().getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(flowListenerFailed.get().getTaskRunList(), is(nullValue()));
        assertThat(flowListenerFailed.get().getState().getCurrent(), is(State.Type.FAILED));
    }
}
