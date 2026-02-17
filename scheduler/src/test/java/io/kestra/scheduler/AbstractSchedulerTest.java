package io.kestra.scheduler;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.flow.Sleep;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@KestraTest(rebuildContext = true)
abstract public class AbstractSchedulerTest {
    public final static String TENANT_ID = "main";

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected Optional<SchedulerTriggerStateInterface> triggerState;

    @Inject
    protected ExecutionService executionService;

    public static FlowWithSource createThreadFlow() {
        return createThreadFlow(null);
    }

    public static FlowWithSource createThreadFlow(String workerGroup) {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .workerGroup(workerGroup == null ? null : new WorkerGroup(workerGroup, null))
            .build();

        return createFlow(null, Collections.singletonList(schedule), List.of(
            PluginDefault.builder()
                .type(UnitTest.class.getName())
                .values(Map.of("defaultInjected", "done"))
                .build()
        ));
    }

    /**
     * @deprecated try to use {@link AbstractSchedulerTest#createFlow(String, List)} with 'tenantId' instead to be
     * extra sure these tests do not share resources
     */
    @Deprecated
    protected static FlowWithSource createFlow(List<AbstractTrigger> triggers) {
        return createFlow(TENANT_ID, triggers);
    }

    protected static FlowWithSource createFlow(String tenantId, List<AbstractTrigger> triggers) {
        return createFlow(tenantId, triggers, null);
    }

    protected static FlowWithSource createFlow(String tenantId, List<AbstractTrigger> triggers, List<PluginDefault> list) {
        FlowWithSource.FlowWithSourceBuilder<?, ?> builder = FlowWithSource.builder()
            .id(IdUtils.create())
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .inputs(List.of(
                StringInput.builder()
                    .type(Type.STRING)
                    .id("testInputs")
                    .required(false)
                    .defaults(Property.ofValue("test"))
                    .build(),
                StringInput.builder()
                    .type(Type.STRING)
                    .id("def")
                    .required(false)
                    .defaults(Property.ofValue("awesome"))
                    .build()
            ))
            .revision(1)
            .labels(
                List.of(
                    new Label("flow-label-1", "flow-label-1"),
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .triggers(triggers)
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format(Property.ofExpression("{{ inputs.testInputs }}"))
                .build()));

        if (list != null) {
            builder.pluginDefaults(list);
        }

        FlowWithSource flow = builder.build();
        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
    }

    protected static FlowWithSource createLongRunningFlow(String tenantId, List<AbstractTrigger> triggers, List<PluginDefault> list) {
        return createFlow(tenantId, triggers, list)
            .toBuilder()
            .tasks(
                Collections.singletonList(
                    Sleep.builder().id("sleep").type(Sleep.class.getName()).duration(Property.ofValue(Duration.ofSeconds(125))).build()
                )
            )
            .build();
    }

    protected void terminateExecution(Execution execution, Trigger trigger, FlowWithSource flow) throws QueueException {
        terminateExecution(execution, State.Type.SUCCESS, trigger, flow);
    }

    protected void terminateExecution(Execution execution, State.Type newState, Trigger trigger, FlowWithSource flow) throws QueueException {
        if (triggerState.isEmpty()) {
            throw new IllegalStateException("No triggerState available in the bean factory");
        }

        Execution terminated = execution.withState(newState);
        executionQueue.emit(terminated);
        triggerState.get().findLast(trigger)
            .ifPresent(t -> triggerState.get().update(executionService.resetExecution(flow, terminated, t)));
    }


    protected static int COUNTER = 0;

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class UnitTest extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(2);

        private String defaultInjected;

        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws InterruptedException {
            COUNTER++;

            if (COUNTER % 2 == 0) {
                Thread.sleep(4000);

                return Optional.empty();
            } else {
                Execution execution = Execution.builder()
                    .id(IdUtils.create())
                    .tenantId(context.getTenantId())
                    .namespace(context.getNamespace())
                    .flowId(context.getFlowId())
                    .flowRevision(conditionContext.getFlow().getRevision())
                    .state(new State())
                    .trigger(ExecutionTrigger.builder()
                        .id(this.getId())
                        .type(this.getType())
                        .variables(ImmutableMap.of(
                            "counter", COUNTER,
                            "defaultInjected", defaultInjected == null ? "ko" : defaultInjected
                        ))
                        .build()
                    )
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
