package io.kestra.plugin.core.http;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Worker;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.DefaultScheduler;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
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
                AbstractScheduler scheduler = new DefaultScheduler(
                        this.applicationContext,
                        this.flowListenersService,
                        this.triggerState
                );
                Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            Runnable receive = executionQueue.receive(TriggerTest.class, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen")) {
                    last.set(execution.getLeft());

                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/http-listen.yaml")));

            try {
                assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            } finally {
                receive.run();
            }
        }
    }

    @Test
    void trigger_EncryptedBody() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        try (
                AbstractScheduler scheduler = new DefaultScheduler(
                        this.applicationContext,
                        this.flowListenersService,
                        this.triggerState
                );
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            Runnable receive = executionQueue.receive(TriggerTest.class, execution -> {
                if (execution.getLeft().getFlowId().equals("http-listen-encrypted")) {
                    last.set(execution.getLeft());

                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows/valids/http-listen-encrypted.yaml")));

            try {
                assertTrue(queueCount.await(1, TimeUnit.MINUTES));
            } finally {
                receive.run();
            }
        }
    }
}
