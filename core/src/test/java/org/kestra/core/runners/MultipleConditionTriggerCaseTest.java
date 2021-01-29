package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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

    public void trigger() throws InterruptedException, TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        ConcurrentHashMap<String, Execution> ended = new ConcurrentHashMap<>();
        Flow flow = flowRepository.findById("org.kestra.tests", "trigger-multiplecondition-listener").orElseThrow();

        executionQueue.receive(execution -> {
            synchronized (ended) {
                if (execution.getState().getCurrent() == State.Type.SUCCESS) {
                    if (!ended.containsKey(execution.getId())) {
                        System.out.println(execution.getFlowId());
                        ended.put(execution.getId(), execution);
                        countDownLatch.countDown();
                    }
                }
            }
        });

        // first one
        Execution execution = runnerUtils.runOne("org.kestra.tests", "trigger-multiplecondition-flow-a", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // wait a little to be sure that the trigger is not launching execution
        countDownLatch.await(1, TimeUnit.SECONDS);
        assertThat(ended.size(), is(1));

        MultipleConditionStorageInterface multipleConditionStorage = applicationContext.getBean(MultipleConditionStorageInterface.class);

        // storage is filed
        MultipleConditionWindow multiple = multipleConditionStorage.get(flow, "multiple").orElseThrow();
        assertThat(multiple.getResults().get("flow-a"), is(true));

        // second one
        execution = runnerUtils.runOne("org.kestra.tests", "trigger-multiplecondition-flow-b", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger is done
        countDownLatch.await(5, TimeUnit.SECONDS);
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
        assertThat(triggerExecution.getTrigger().getVariables().get("namespace"), is("org.kestra.tests"));
        assertThat(triggerExecution.getTrigger().getVariables().get("flowId"), is("flowId -> trigger-multiplecondition-flow-b"));

        // control that storage was reset to avoid multiple execution
        multiple = multipleConditionStorage.get(flow, "multiple").orElseThrow();
        assertThat(multiple.getResults().size(), is(1));
    }
}
