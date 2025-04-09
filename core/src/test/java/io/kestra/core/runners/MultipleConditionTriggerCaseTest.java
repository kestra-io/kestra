package io.kestra.core.runners;

import io.kestra.core.queues.QueueException;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class MultipleConditionTriggerCaseTest {

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ApplicationContext applicationContext;

    public void trigger() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        ConcurrentHashMap<String, Execution> ended = new ConcurrentHashMap<>();
        List<String> watchedExecutions = List.of("trigger-multiplecondition-flow-a",
            "trigger-multiplecondition-flow-b",
            "trigger-multiplecondition-listener"
        );

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (watchedExecutions.contains(execution.getFlowId()) && execution.getState().getCurrent() == State.Type.SUCCESS) {
                ended.put(execution.getId(), execution);
                countDownLatch.countDown();
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger",
            "trigger-multiplecondition-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait a little to be sure that the trigger is not launching execution
        Thread.sleep(1000);
        assertThat(ended.size()).isEqualTo(1);

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger",
            "trigger-multiplecondition-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(ended.size()).isEqualTo(3);

        Flow flow = flowRepository.findById(null, "io.kestra.tests.trigger",
            "trigger-multiplecondition-listener").orElseThrow();
        Execution triggerExecution = ended.entrySet()
            .stream()
            .filter(e -> e.getValue().getFlowId().equals(flow.getId()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseThrow();

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(triggerExecution.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(triggerExecution.getTrigger().getVariables().get("namespace")).isEqualTo("io.kestra.tests.trigger");
        assertThat(triggerExecution.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-multiplecondition-flow-b");
    }

    public void failed() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> listener = new AtomicReference<>();
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("trigger-flow-listener-namespace-condition")
                && execution.getState().getCurrent().isTerminated()) {
                listener.set(execution);
                countDownLatch.countDown();
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger",
            "trigger-multiplecondition-flow-c", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait a little to be sure that the trigger is not launching execution
        Thread.sleep(1000);
        assertThat(listener.get()).isNull();

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger",
            "trigger-multiplecondition-flow-d", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger was not done
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(listener.get()).isNotNull();
        assertThat(listener.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerPreconditions()
        throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> flowTrigger = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS && execution.getFlowId()
                .equals("flow-trigger-preconditions-flow-listen")) {
                flowTrigger.set(execution);
                countDownLatch.countDown();
            }
        });

        // flowA
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowB: we trigger it two times, as flow-trigger-flow-preconditions-flow-listen is configured with resetOnSuccess: false it should be triggered two times
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(flowTrigger.get()).isNotNull();

        Execution triggerExecution = flowTrigger.get();
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        assertThat((Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs")).containsEntry("some", "value");
    }

    public void flowTriggerPreconditionsMergeOutputs() throws QueueException, TimeoutException, InterruptedException {
        // we do the same as in flowTriggerPreconditions() but we trigger flows in the opposite order to be sure that outputs are merged
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> flowTrigger = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS && execution.getFlowId()
                .equals("flow-trigger-preconditions-flow-listen")) {
                flowTrigger.set(execution);
                countDownLatch.countDown();
            }
        });

        // flowB
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowA
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(flowTrigger.get()).isNotNull();

        Execution triggerExecution = flowTrigger.get();
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        assertThat((Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs")).containsEntry("some", "value");
    }
}
