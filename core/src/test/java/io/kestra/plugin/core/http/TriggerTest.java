package io.kestra.plugin.core.http;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Worker;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
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

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class TriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

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
                Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen")) {
                    last.set(execution.getLeft());

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

        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        // scheduler
        try (
            AbstractScheduler scheduler = new JdbcScheduler(
                this.applicationContext,
                this.flowListenersService
            );
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen-encrypted")) {
                    last.set(execution.getLeft());

                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/http-listen-encrypted.yaml")));

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            worker.shutdown();
            receive.blockLast();
        }
    }
}
