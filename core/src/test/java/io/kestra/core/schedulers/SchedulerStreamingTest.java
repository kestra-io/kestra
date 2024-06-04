package io.kestra.core.schedulers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerStreamingTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    private static Flow createFlow(Boolean failed) {
        RealtimeUnitTest schedule = RealtimeUnitTest.builder()
            .id("stream")
            .type(RealtimeUnitTest.class.getName())
            .failed(failed)
            .executionsToCreate(10)
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    private void run(Flow flow, CountDownLatch queueCount, Consumer<List<Execution>> consumer) throws Exception {
        // wait for execution
        Flux<Execution> receive = TestsUtils.receive(executionQueue, SchedulerStreamingTest.class, either -> {
            queueCount.countDown();
        });

        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (
            AbstractScheduler scheduler = new DefaultScheduler(
                applicationContext,
                flowListenersServiceSpy,
                triggerState
            );
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            // start the worker as it execute polling triggers
            worker.run();
            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
            consumer.accept(receive.collectList().block());
        }
    }

    @Test
    void simple() throws Exception {
        Flow flow = createFlow(false);
        CountDownLatch queueCount = new CountDownLatch(10);

        this.run(
            flow,
            queueCount,
            executionList -> {
                List<Execution> executionCount = executionList.stream()
                    .filter(e -> e.getNamespace().equals(flow.getNamespace()) && e.getFlowId().equals(flow.getId()))
                    .toList();

                Execution last = executionList.stream()
                    .filter(e -> e.getTrigger().getVariables().get("executionCount").equals(10))
                    .findAny()
                    .orElseThrow();

                assertThat(executionCount.size(), is(10));
                assertThat(executionList.size(), is(10));
                assertThat(SchedulerStreamingTest.startedEvaluate.get(false), is(1));
                assertThat(last.getTrigger().getVariables().get("startedEvaluate"), is(1));
            }
        );
    }

    @Test
    void failed() throws Exception {
        Flow flow = createFlow(true);
        CountDownLatch queueCount = new CountDownLatch(20);

        this.run(
            flow,
            queueCount,
            executionList -> {
                List<Execution> executionCount = executionList.stream()
                    .filter(e -> e.getNamespace().equals(flow.getNamespace()) && e.getFlowId().equals(flow.getId()))
                    .toList();

                assertThat(executionCount.size(), greaterThan(10));
                assertThat(executionList.size(), greaterThan(10));
                assertThat(SchedulerStreamingTest.startedEvaluate.get(true), greaterThan(1));
            }
        );
    }

    protected static Map<Boolean, Integer> startedEvaluate = new HashMap<>();

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class RealtimeUnitTest extends AbstractTrigger implements RealtimeTriggerInterface {
        private Integer executionsToCreate;

        @Builder.Default
        private Integer executionCount = 0;

        @Builder.Default
        private Boolean failed = false;

        public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            startedEvaluate.compute(this.failed, (val, integer) -> integer == null ? 1 : integer + 1);

            return Flux.create(fluxSink -> {
                for (int i = 0; i < executionsToCreate; i++) {
                    executionCount++;

                    try {
                        Execution execution = TriggerService.generateExecution(
                            this,
                            conditionContext,
                            context,
                            ImmutableMap.of(
                            "startedEvaluate", startedEvaluate.get(failed),
                            "executionCount", executionCount
                            )
                        );

                        if (failed && i == 9) {
                            fluxSink.error(new Exception("Failed"));
                            break;
                        } else {
                            fluxSink.next(execution);
                        }
                    } catch (Exception e) {
                        fluxSink.error(e);
                    }
                }

                fluxSink.complete();
            }, FluxSink.OverflowStrategy.BUFFER);
        }
    }
}
