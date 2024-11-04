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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
        Flow flow = flowRepository.findById(null, "io.kestra.tests.trigger", "trigger-multiplecondition-listener").orElseThrow();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            synchronized (ended) {
                if (execution.getState().getCurrent() == State.Type.SUCCESS && !execution.getFlowId().equals("trigger-flow-listener-namespace-condition")) {
                    if (!ended.containsKey(execution.getId())) {
                        ended.put(execution.getId(), execution);
                        countDownLatch.countDown();
                    }
                }
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-multiplecondition-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // wait a little to be sure that the trigger is not launching execution
        countDownLatch.await(1, TimeUnit.SECONDS);
        assertThat(ended.size(), is(1));

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-multiplecondition-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger is done
        countDownLatch.await(10, TimeUnit.SECONDS);
        receive.blockLast();
        assertThat(ended.size(), is(3));

        Execution triggerExecution = ended.entrySet()
            .stream()
            .filter(e -> e.getValue().getFlowId().equals(flow.getId()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseThrow();

        assertThat(triggerExecution.getTaskRunList().size(), is(1));
        assertThat(triggerExecution.getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(triggerExecution.getTrigger().getVariables().get("executionId"), is(execution.getId()));
        assertThat(triggerExecution.getTrigger().getVariables().get("namespace"), is("io.kestra.tests.trigger"));
        assertThat(triggerExecution.getTrigger().getVariables().get("flowId"), is("trigger-multiplecondition-flow-b"));
    }

    public void failed() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ConcurrentHashMap<String, Execution> ended = new ConcurrentHashMap<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            synchronized (ended) {
                Execution execution = either.getLeft();
                if (execution.getState().getCurrent().isTerminated() && !execution.getFlowId().equals("trigger-flow-listener-namespace-condition")) {
                    if (!ended.containsKey(execution.getId())) {
                        ended.put(execution.getId(), execution);
                        countDownLatch.countDown();
                    }
                }
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-multiplecondition-flow-c", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        // wait a little to be sure that the trigger is not launching execution
        Thread.sleep(1000);
        assertThat(ended.size(), is(1));

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-multiplecondition-flow-d", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger was not done
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(ended.size(), is(2));
    }

    public void flowFilters() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> flowFilters = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS && execution.getFlowId().equals("flow-trigger-flow-filters-flow-listen")) {
                flowFilters.set(execution);
                countDownLatch.countDown();
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.flow-filters", "flow-trigger-flow-filters-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.flow-filters", "flow-trigger-flow-filters-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger is done
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(flowFilters.get(), notNullValue());

        Execution triggerExecution = flowFilters.get();

        assertThat(triggerExecution.getTaskRunList().size(), is(1));
        assertThat(triggerExecution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void executionFilters() throws InterruptedException, TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> flowFilters = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.SUCCESS && execution.getFlowId().equals("flow-trigger-execution-filters-flow-listen")) {
                flowFilters.set(execution);
                countDownLatch.countDown();
            }
        });

        // first one
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.execution-filters", "flow-trigger-execution-filters-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // second one
        execution = runnerUtils.runOne(null, "io.kestra.tests.trigger.execution-filters", "flow-trigger-execution-filters-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger is done
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();
        assertThat(flowFilters.get(), notNullValue());

        Execution triggerExecution = flowFilters.get();

        assertThat(triggerExecution.getTaskRunList().size(), is(1));
        assertThat(triggerExecution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
