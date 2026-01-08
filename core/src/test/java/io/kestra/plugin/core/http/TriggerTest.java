package io.kestra.plugin.core.http;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(rebuildContext = true)
class TriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowListenersInterface flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Test
    void trigger() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (
                AbstractScheduler scheduler = new JdbcScheduler(
                        this.applicationContext,
                        this.flowListenersService
                );
                Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null);
        ) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen")) {
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/http-listen.yaml")));

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            receive.blockLast();
        }
    }

    @Test
    void trigger_EncryptedBody() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (
            AbstractScheduler scheduler = new JdbcScheduler(
                this.applicationContext,
                this.flowListenersService
            );
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen-encrypted")) {
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/http-listen-encrypted.yaml")));

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            receive.blockLast();
        }
    }
    @Test
    void labelsWithWorkerTriggerAndRenderedProperties() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (
            AbstractScheduler scheduler = new JdbcScheduler(
                this.applicationContext,
                this.flowListenersService
            );
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                Execution finalExecution = execution.getLeft();
                assertThat(finalExecution).isNotNull();
                assertThat(finalExecution.getFlowId()).isEqualTo("worker-trigger-with-labels");
                assertThat(finalExecution.getState().getCurrent()).isEqualTo(State.Type.CREATED);
                Map<Object, Object> resolvedLabels =
                    finalExecution.getLabels().stream()
                        .collect(Collectors.toMap(
                            Label::key,
                            Label::value
                        ));
                assertThat(resolvedLabels).hasSize(3);
                assertThat(resolvedLabels.get("course")).isEqualTo("dsa");
                assertThat(resolvedLabels.get("system.from")).isEqualTo("trigger");
                assertThat(resolvedLabels.containsKey("correlationId"));
                queueCount.countDown();
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/worker-trigger-with-labels.yaml")));

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            receive.blockLast();
        }
    }
}
