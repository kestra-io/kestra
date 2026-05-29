package io.kestra.cli;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.core.ConditionTimeoutException;

import io.kestra.core.runners.*;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.worker.Controller;
import io.kestra.executor.DefaultExecutor;
import io.kestra.worker.systemworker.SystemWorker;

import io.micronaut.context.BeanProvider;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StandAloneRunner implements Runnable, AutoCloseable {
    @Setter
    protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter
    protected boolean schedulerEnabled = true;
    @Setter
    protected boolean workerEnabled = true;
    @Setter
    protected boolean indexerEnabled = true;
    @Setter
    protected boolean controllerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private DefaultExecutor defaultExecutor;

    @Inject
    private Provider<Controller> controllerProvider;

    @Inject
    private Provider<Worker> workerProvider;

    @Inject
    private Provider<Scheduler> schedulerProvider;

    @Inject
    private Provider<Indexer> indexerProvider;

    @Inject
    private BeanProvider<SystemWorker> systemWorkerProvider;

    @Inject
    private ServerConfig serverConfig;

    private final List<Service> servers = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService poolExecutor;

    @Override
    public void run() {
        running.set(true);

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");
        poolExecutor.execute(defaultExecutor);

        if (controllerEnabled) {
            Controller controller = controllerProvider.get();
            poolExecutor.execute(controller::start);
            servers.add(controller);
        }

        if (workerEnabled) {
            Worker worker = workerProvider.get();
            poolExecutor.execute(() -> worker.start(workerThread));
            servers.add(worker);
        }

        if (schedulerEnabled) {
            Scheduler scheduler = schedulerProvider.get();
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        if (indexerEnabled) {
            Indexer indexer = indexerProvider.get();
            poolExecutor.execute(indexer);
            servers.add(indexer);
        }

        // start the embedded SystemWorker (always present in STANDALONE mode)
        SystemWorker systemWorker = systemWorkerProvider.get();
        poolExecutor.execute(systemWorker::start);
        servers.add(systemWorker);

        try {
            Await.await().atMost(getRunningTimeout()).until(
                () -> servers.stream().allMatch(s -> Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning())
            );
        } catch (ConditionTimeoutException e) {
            throw new RuntimeException(
                servers.stream().filter(s -> !Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning())
                    .map(Service::getClass)
                    .toList() + " not started in time"
            );
        }
    }

    private Duration getRunningTimeout() {
        return Optional.ofNullable(serverConfig.standalone())
            .map(ServerConfig.Standalone::running)
            .map(ServerConfig.Standalone.Running::timeout)
            .orElse(Duration.ofMinutes(1));
    }

    public boolean isRunning() {
        return this.running.get();
    }

    @PreDestroy
    @Override
    public void close() {
        if (this.poolExecutor != null) {
            this.poolExecutor.shutdown();
        }
    }
}
