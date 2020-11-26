package org.kestra.core.schedulers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.RunContext;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;

@MicronautTest
abstract public class AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    protected  static Flow createFlow(List<AbstractTrigger> triggers) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("org.kestra.unittest")
            .revision(1)
            .triggers(triggers)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class UnitTest extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(2);

        @Builder.Default
        private transient int counter = 0;

        public Optional<Execution> evaluate(RunContext runContext, TriggerContext context) throws InterruptedException {
            counter++;

            if (counter % 2 == 0) {
                Thread.sleep(4000);

                return Optional.empty();
            } else {
                Execution execution = Execution.builder()
                    .id(IdUtils.create())
                    .namespace(context.getNamespace())
                    .flowId(context.getFlowId())
                    .flowRevision(context.getFlowRevision())
                    .state(new State())
                    .variables(ImmutableMap.of("counter", counter))
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
