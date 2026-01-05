package io.kestra.scheduler;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.core.tasks.test.FailingPollingTrigger;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.core.condition.Expression;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.plugin.core.execution.Fail;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerPollingTriggerTest extends AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    private FlowListeners flowListenersService;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Test
    void pollingTrigger() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = createPollingTrigger(null).build();
        Flow flow = createPollingTriggerFlow(pollingTrigger);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();

            queueCount.await(10, TimeUnit.SECONDS);
            receive.blockLast();

            assertThat(queueCount.getCount()).isEqualTo(0L);
            assertThat(last.get()).isNotNull();
            assertTrue(last.get().getLabels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
            assertTrue(last.get().getLabels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));
        }
    }

    @Test
    void pollingTriggerStopAfter() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = createPollingTrigger(List.of(State.Type.FAILED)).build();
        FlowWithSource flow = createPollingTriggerFlow(pollingTrigger)
            .toBuilder()
            .tasks(List.of(Fail.builder().id("fail").type(Fail.class.getName()).build()))
            .build();
        flowRepository.create(GenericFlow.of(flow));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(2);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Flux<Execution> receive = TestsUtils.receive(executionQueue, throwConsumer(execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();

                    if (execution.getLeft().getState().getCurrent() == State.Type.CREATED) {
                        terminateExecution(execution.getLeft(), State.Type.FAILED, Trigger.of(flow, pollingTrigger), flow);
                    }
                }
            }));

            worker.run();
            scheduler.run();

            queueCount.await(10, TimeUnit.SECONDS);
            receive.blockLast();

            assertThat(queueCount.getCount()).isEqualTo(0L);
            assertThat(last.get()).isNotNull();
            assertTrue(last.get().getLabels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
            assertTrue(last.get().getLabels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));

            // Assert that the trigger is now disabled.
            // It needs to await on assertion as it will be disabled AFTER we receive a success execution.
            Trigger trigger = Trigger.of(flow, pollingTrigger);
            Await.until(() -> this.triggerState.findLast(trigger).map(TriggerContext::getDisabled).orElse(false).booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(10));
        }
    }

    @Test
    void failedEvaluationTest() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = createPollingTrigger(null)
            .conditions(
                List.of(
                    Expression.builder()
                        .type(Expression.class.getName())
                        .expression(Property.ofExpression("{{ trigger.date | date() < now() }}"))
                        .build()
                ))
            .build();
        Flow flow = createPollingTriggerFlow(pollingTrigger);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();

            queueCount.await(10, TimeUnit.SECONDS);
            // close the execution queue consumer
            receive.blockLast();

            assertThat(queueCount.getCount()).isEqualTo(0L);
            assertThat(last.get()).isNotNull();
            assertThat(last.get().getFlowRevision()).isNotNull();
            assertThat(last.get().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        }
    }

    @Test
    void pollingTriggerFailOnTriggerError() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        FailingPollingTrigger pollingTrigger = FailingPollingTrigger.builder()
            .id("failing")
            .type(FailingPollingTrigger.class.getName())
            .failOnTriggerError(true)
            .build();
        Flow flow = createPollingTriggerFlow(pollingTrigger);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();

            assertTrue(queueCount.await(10, TimeUnit.SECONDS));
            receive.blockLast();

            assertThat(last.get()).isNotNull();
            assertTrue(last.get().getState().isFailed());
        }
    }

    @Test
    void shouldDisableTriggerOnInvalidOverflowInterval() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        OverflowIntervalTrigger overflow = OverflowIntervalTrigger.builder()
            .id("overflow-interval")
            .type(OverflowIntervalTrigger.class.getName())
            .build();

        FlowWithSource flow = createPollingTriggerFlow(overflow);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            worker.run();
            scheduler.run();

            Trigger key = Trigger.of(flow, overflow);

            Await.until(() -> this.triggerState.findLast(key).map(TriggerContext::getDisabled).get().booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(15));
        }
    }

    private FlowWithSource createPollingTriggerFlow(AbstractTrigger pollingTrigger) {
        return createFlow(Collections.singletonList(pollingTrigger));
    }

    private PollingTrigger.PollingTriggerBuilder<?, ?> createPollingTrigger(List<State.Type> stopAfter) {
        return PollingTrigger.builder()
            .id("polling-trigger")
            .type(PollingTrigger.class.getName())
            .duration(500L)
            .stopAfter(stopAfter);
    }

    private AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class OverflowIntervalTrigger extends AbstractTrigger implements PollingTriggerInterface {
        // we set a large interval which will throw an exception
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(Long.MAX_VALUE);

        @Override
        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            return Optional.empty();
        }
    }
}