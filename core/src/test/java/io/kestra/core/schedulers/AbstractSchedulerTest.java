package io.kestra.core.schedulers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.TaskDefault;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@MicronautTest
abstract public class AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    protected  static Flow createFlow(List<AbstractTrigger> triggers) {
        return createFlow(triggers, null);
    }

    protected  static Flow createFlow(List<AbstractTrigger> triggers, List<TaskDefault> list) {
        Flow.FlowBuilder<?, ?> flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .inputs(List.of(
                Input.builder()
                    .type(Input.Type.STRING)
                    .name("testInputs")
                    .required(false)
                    .defaults("test")
                    .build(),
                Input.builder()
                    .type(Input.Type.STRING)
                    .name("def")
                    .required(false)
                    .defaults("awesome")
                    .build()
            ))
            .revision(1)
            .triggers(triggers)
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("{{ inputs.testInputs }}")
                .build()));

        if (list != null) {
            flow.taskDefaults(list);
        }

        return flow
            .build();
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
                    .flowRevision(context.getFlowRevision())
                    .state(new State())
                    .variables(ImmutableMap.of(
                        "counter", COUNTER,
                        "defaultInjected", defaultInjected == null ? "ko" : defaultInjected
                    ))
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
