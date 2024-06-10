package io.kestra.core.runners;

import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

@Slf4j
@Singleton
@Requires(missingBeans = RunnerInterface.class)
public class StandAloneRunner implements RunnerInterface, AutoCloseable {
    private ExecutorService poolExecutor;
    @Setter protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected boolean schedulerEnabled = true;
    @Setter protected boolean workerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.server.standalone.running.timeout:PT1M}")
    private Duration runningTimeout;

    private final List<Service> servers = new ArrayList<>();

    private boolean running = false;

    @Override
    public void run() {
        this.running = true;

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");
        poolExecutor.execute(applicationContext.getBean(ExecutorInterface.class));

        if (workerEnabled) {
            // FIXME: For backward-compatibility with Kestra 0.15.x and earliest we still used UUID for Worker ID instead of IdUtils
            String workerID = UUID.randomUUID().toString();
            Worker worker = applicationContext.createBean(Worker.class, workerID, workerThread, null);
            applicationContext.registerSingleton(worker); //
            poolExecutor.execute(worker);
            servers.add(worker);
        }

        if (schedulerEnabled) {
            AbstractScheduler scheduler = applicationContext.getBean(AbstractScheduler.class);
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        if (applicationContext.containsBean(IndexerInterface.class)) {
            IndexerInterface indexer = applicationContext.getBean(IndexerInterface.class);
            poolExecutor.execute(indexer);
            servers.add(indexer);
        }

        try {
            Await.until(() -> servers.stream().allMatch(s -> Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning()), null, runningTimeout);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    @Override
    public void close() throws Exception {
        this.poolExecutor.shutdown();
    }
}
