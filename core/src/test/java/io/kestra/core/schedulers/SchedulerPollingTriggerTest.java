package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.Worker;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Causes all sort of issues on CI")
@MicronautTest(transactional = false)
public class SchedulerPollingTriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    private FlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;


    @Test
    void pollingTrigger() throws Exception {
        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = new DefaultScheduler(
                this.applicationContext,
                this.flowListenersService,
                this.triggerState
            );
            Worker worker = new Worker(applicationContext, 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Runnable executionQueueStop = executionQueue.receive(execution -> {
                last.set(execution.getLeft());

                queueCount.countDown();
                assertThat(execution.getLeft().getFlowId(), is("polling-trigger"));
            });

            worker.run();
            scheduler.run();

            repositoryLoader.load(Objects.requireNonNull(SchedulerPollingTriggerTest.class.getClassLoader().getResource("flows/trigger")));

            queueCount.await(10, TimeUnit.SECONDS);
            // close the execution queue consumer
            executionQueueStop.run();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(last.get(), notNullValue());
        }
    }
}
