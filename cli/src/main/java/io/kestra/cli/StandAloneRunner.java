package io.kestra.cli;

import io.kestra.core.runners.*;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.worker.DefaultWorker;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("try")
@Slf4j
public class StandAloneRunner implements Runnable, AutoCloseable {
    @Setter protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected boolean schedulerEnabled = true;
    @Setter protected boolean workerEnabled = true;
    @Setter protected boolean indexerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.server.standalone.running.timeout:PT1M}")
    private Duration runningTimeout;

    private final List<Service> servers = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService poolExecutor;

    @Override
    public void run() {
        running.set(true);

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");
        poolExecutor.execute(applicationContext.getBean(ExecutorInterface.class));

        if (workerEnabled) {
            // FIXME: For backward-compatibility with Kestra 0.15.x and earliest we still used UUID for Worker ID instead of IdUtils
            String workerID = UUID.randomUUID().toString();
            Worker worker = applicationContext.createBean(DefaultWorker.class, workerID, workerThread, null);
            applicationContext.registerSingleton(worker); //
            poolExecutor.execute(worker);
            servers.add(worker);
        }

        if (schedulerEnabled) {
            Scheduler scheduler = applicationContext.getBean(Scheduler.class);
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        if (indexerEnabled) {
            Indexer indexer = applicationContext.getBean(Indexer.class);
            poolExecutor.execute(indexer);
            servers.add(indexer);
        }

        try {
            Await.until(() -> servers.stream().allMatch(s -> Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning()), null, runningTimeout);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                servers.stream().filter(s -> !Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning())
                    .map(Service::getClass)
                    .toList() + " not started in time");
        }
    }

    public boolean isRunning() {
        return this.running.get();
    }

    @PreDestroy
    @Override
    public void close() throws Exception {
        if (this.poolExecutor != null) {
            this.poolExecutor.shutdown();
        }
    }
}
