package org.kestra.core.schedulers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Scheduler implements Runnable {

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<Flow> flowQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowPool pool;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ThreadMainFactoryBuilder threadFactoryBuilder;

    private ScheduledExecutorService executor;

    private void runSchedules(Instant now) {
        log.debug("Schedule for instant " + now.toString());
        pool.triggerReadyFlows(now);
    }

    @Override
    public void run() {
        executor = Executors.newScheduledThreadPool(1,
            threadFactoryBuilder.build("scheduler-%d")
        );

        executor.scheduleAtFixedRate(
            () -> {
                try {
                    Instant now = Instant.now();
                    runSchedules(now);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            },
            1,
            1,
            TimeUnit.SECONDS
        );

        flowQueue.receive(flow -> {
            if (flow.hasNextSchedule()) {
                log.info("Scheduling flow " + flow);
                pool.upsert(flow);
            } else {
                log.info("Removing flow from pool " + flow);
                pool.remove(flow);
            }
        });
    }
}
