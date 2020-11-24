package org.kestra.core.schedulers;

import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerThreadTest extends AbstractSchedulerTest {
    private static Flow createThreadFlow() {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    @Test
    void thread() throws Exception {
        // mock flow listeners
        FlowListenersService flowListenersServiceSpy = spy(this.flowListenersService);
        ExecutionRepositoryInterface executionRepositorySpy = spy(this.executionRepository);
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = createThreadFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(executionRepositorySpy)
            .findById(any());

        // scheduler
        try (Scheduler scheduler = new Scheduler(
            applicationContext,
            executorsUtils,
            executionQueue,
            flowListenersServiceSpy,
            executionRepositorySpy,
            triggerContextRepository
        )) {

            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(SchedulerThreadTest.class, execution -> {
                last.set(execution);
                queueCount.countDown();
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            scheduler.run();
            queueCount.await();

            assertThat(last.get().getVariables().get("counter"), is(3));
        }
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
