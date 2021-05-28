package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@Singleton
public class FlowCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void waitSuccess() throws Exception {
        this.run("OK", State.Type.SUCCESS, 2);
    }

    public void waitFailed() throws Exception {
        this.run("THIRD", State.Type.FAILED, 4);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    void run(String input, State.Type type, int count) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(execution -> {
            if (execution.getFlowId().equals("switch") && execution.getState().getCurrent().isTerninated()) {
                countDownLatch.countDown();
                triggered.set(execution);
            }
        });

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "task-flow",
            null,
            (f, e) -> ImmutableMap.of("string", input),
            Duration.ofMinutes(1)
        );

        countDownLatch.await(1, TimeUnit.MINUTES);

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(type));

        assertThat(execution.getTaskRunList().get(0).getOutputs().get("executionId"), is(triggered.get().getId()));
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("state"), is(triggered.get().getState().getCurrent().name()));

        assertThat(triggered.get().getTrigger().getType(), is(Flow.class.getName()));
        assertThat(triggered.get().getTrigger().getVariables().get("executionId"), is(execution.getId()));
        assertThat(triggered.get().getTrigger().getVariables().get("flowId"), is(execution.getFlowId()));
        assertThat(triggered.get().getTrigger().getVariables().get("namespace"), is(execution.getNamespace()));

        assertThat(triggered.get().getTaskRunList(), hasSize(count));
        assertThat(triggered.get().getState().getCurrent(), is(type));
    }
}
