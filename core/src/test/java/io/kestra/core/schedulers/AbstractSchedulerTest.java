package io.kestra.core.schedulers;

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
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
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

@KestraTest
abstract public class AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public static Flow createThreadFlow() {
        return createThreadFlow(null);
    }

    public static Flow createThreadFlow(String workerGroup) {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .workerGroup(workerGroup == null ? null : new WorkerGroup(workerGroup, null))
            .build();

        return createFlow(Collections.singletonList(schedule), List.of(
            PluginDefault.builder()
                .type(UnitTest.class.getName())
                .values(Map.of("defaultInjected", "done"))
                .build()
        ));
    }

    protected static FlowWithSource createFlow(List<AbstractTrigger> triggers) {
        return createFlow(triggers, null);
    }

    protected static FlowWithSource createFlow(List<AbstractTrigger> triggers, List<PluginDefault> list) {
        Flow.FlowBuilder<?, ?> builder = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .inputs(List.of(
                StringInput.builder()
                    .type(Type.STRING)
                    .id("testInputs")
                    .required(false)
                    .defaults("test")
                    .build(),
                StringInput.builder()
                    .type(Type.STRING)
                    .id("def")
                    .required(false)
                    .defaults("awesome")
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
                .format(new Property<>("{{ inputs.testInputs }}"))
                .build()));

        if (list != null) {
            builder.pluginDefaults(list);
        }

        Flow flow = builder.build();
        return FlowWithSource.of(flow, flow.generateSource());
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
