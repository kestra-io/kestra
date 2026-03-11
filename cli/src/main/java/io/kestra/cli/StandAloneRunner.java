package io.kestra.cli;

import io.kestra.core.runners.*;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.worker.Controller;
import io.kestra.executor.DefaultExecutor;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    @Setter protected boolean controllerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private DefaultExecutor defaultExecutor;

    @Inject
    private Controller controller;

    @Inject
    private Worker worker;

    @Inject
    private Scheduler scheduler;

    @Inject
    private Indexer indexer;

    @Value("${kestra.server.standalone.running.timeout:PT1M}")
    private Duration runningTimeout;

    private final List<Service> servers = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService poolExecutor;

    @Override
    public void run() {
        running.set(true);

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");
        poolExecutor.execute(defaultExecutor);

        if (controllerEnabled) {
            poolExecutor.execute(controller::start);
            servers.add(controller);
        }

        if (workerEnabled) {
            poolExecutor.execute(() -> worker.start(workerThread, null));
            servers.add(worker);
        }

        if (schedulerEnabled) {
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        if (indexerEnabled) {
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
