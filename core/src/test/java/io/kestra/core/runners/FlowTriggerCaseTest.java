package io.kestra.core.runners;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Singleton
public class FlowTriggerCaseTest {

    public static final String NAMESPACE = "io.kestra.tests.trigger";

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private FlowService flowService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    public void trigger(String tenantId) throws InterruptedException, TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, NAMESPACE, "trigger-flow");

        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution flowListenerNoInput = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener-no-inputs"
        );
        Execution flowListener = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener"
        );
        Execution flowListenerNamespace = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener-namespace-condition"
        );

        assertThat(flowListener.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListener.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(flowListener.getTaskRunList().getFirst()).get("value")).isEqualTo("childs: from parents: " + execution.getId());
        assertThat(flowListener.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListener.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        assertThat(flowListener.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");

        assertThat(flowListenerNoInput.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");
        assertThat(flowListenerNoInput.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(flowListenerNamespace.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNamespace.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        // it will be triggered for 'trigger-flow' or any of the 'trigger-flow-listener*', so we only assert that it's one of them
        assertThat(flowListenerNamespace.getTrigger().getVariables().get("flowId"))
            .satisfiesAnyOf(
                arg -> assertThat(arg).isEqualTo("trigger-flow"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener-no-inputs"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener")
            );
    }

    public void triggerWithPause() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.pause", "trigger-flow-with-pause");

        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Execution> triggeredExec = runnerUtils.awaitFlowExecutionNumber(
            4,
            MAIN_TENANT,
            "io.kestra.tests.trigger.pause",
            "trigger-flow-listener-with-pause"
        );

        assertThat(triggeredExec.size()).isEqualTo(4);
        List<Execution> sortedExecs = triggeredExec.stream()
            .sorted(Comparator.comparing(e -> e.getState().getEndDate().orElse(Instant.now())))
            .toList();
        assertThat(sortedExecs.get(0).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(sortedExecs.get(1).getOutputs().get("status")).isEqualTo("PAUSED");
        assertThat(sortedExecs.get(2).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(sortedExecs.get(3).getOutputs().get("status")).isEqualTo("SUCCESS");
    }

    /**
     * Verifies that condition IDs inside a Flow trigger dependsOn are stable across flow updates.
     * <p>
     * When the order of {@code dependsOn} entries is inverted in an updated flow, the state already
     * accumulated for each dependency must be preserved — because each condition ID is a hash of
     * the dependency's content rather than a sequential number.
     * </p>
     */
    public void triggerDependsOnWithStableConditionId() throws TimeoutException, QueueException, FlowProcessingException, DeserializationException, InterruptedException {
        final String namespace = "io.kestra.tests.trigger.stable.condition.id";
        final String listenFlowId = "flow-trigger-stable-condition-id-flow-listen";

        // Given: run flow-a — only one of two conditions satisfied
        Execution executionA = runnerUtils.runOne(MAIN_TENANT, namespace, "flow-trigger-stable-condition-id-flow-a");
        assertThat(executionA.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Then: the listener trigger must NOT fire yet (flow-b not satisfied)
        assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, namespace, listenFlowId, Duration.ofSeconds(3)
        ));

        // When: update the listener flow to invert the conditions order
        FlowWithSource currentListenerFlow = flowRepository.findByIdWithSource(MAIN_TENANT, namespace, listenFlowId).orElseThrow();
        String invertedSource = """
            id: flow-trigger-stable-condition-id-flow-listen
            namespace: io.kestra.tests.trigger.stable.condition.id

            triggers:
              - id: flow
                type: io.kestra.plugin.core.trigger.Flow
                dependsOn:
                  - namespace: io.kestra.tests.trigger.stable.condition.id
                    flowId: flow-trigger-stable-condition-id-flow-b
                    states: [SUCCESS]
                  - namespace: io.kestra.tests.trigger.stable.condition.id
                    flowId: flow-trigger-stable-condition-id-flow-a
                    states: [SUCCESS]

            tasks:
              - id: only
                type: io.kestra.plugin.core.debug.Return
                format: "It works"
            """;
        GenericFlow invertedListenerFlow = GenericFlow.fromYaml(MAIN_TENANT, invertedSource);
        var updated = flowService.update(invertedListenerFlow, currentListenerFlow);

        // the flow metastore is updated async so we wait a little
        await().atMost(Duration.ofSeconds(1))
            .until(() -> {
                var metastoreRevision = flowMetaStore.findById(updated.getTenantId(), updated.getNamespace(), updated.getId(), Optional.empty()).map(it -> it.getRevision());
                return metastoreRevision.isPresent() && metastoreRevision.get().equals(updated.getRevision());
            });

        // When: run flow-b only — flow-a's satisfaction was preserved across the inversion
        // Then: the listener trigger MUST fire (both conditions satisfied via stable hashes)
        Execution executionB = runnerUtils.runOne(MAIN_TENANT, namespace, "flow-trigger-stable-condition-id-flow-b");
        assertThat(executionB.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, namespace, listenFlowId
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
    }

    public void triggerWithConcurrencyLimit(String tenantId) throws QueueException, TimeoutException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, "io.kestra.tests.trigger.concurrency", "trigger-flow-with-concurrency-limit");
        Execution execution2 = runnerUtils.runOne(tenantId, "io.kestra.tests.trigger.concurrency", "trigger-flow-with-concurrency-limit");

        List<Execution> triggeredExec = runnerUtils.awaitFlowExecutionNumber(
            5,
            tenantId,
            "io.kestra.tests.trigger.concurrency",
            "trigger-flow-listener-with-concurrency-limit"
        );

        assertThat(triggeredExec.size()).isEqualTo(5);
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("RUNNING") && e.getOutputs().get("executionId").equals(execution1.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("SUCCESS") && e.getOutputs().get("executionId").equals(execution1.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("QUEUED") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("RUNNING") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("SUCCESS") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
    }
}
