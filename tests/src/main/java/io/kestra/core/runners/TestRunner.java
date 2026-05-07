package io.kestra.core.runners;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.core.ConditionTimeoutException;

import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.worker.Controller;

import io.micronaut.context.ApplicationContext;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("try")
@Slf4j
@Singleton
public class TestRunner implements Runnable, AutoCloseable {
    @Setter
    private int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors()) * 4;
    @Setter
    private boolean schedulerEnabled = true;
    @Setter
    private boolean workerEnabled = true;
    @Setter
    private boolean workerControllerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ServerConfig serverConfig;

    private final List<Service> servers = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService poolExecutor;

    @Override
    public void run() {
        running.set(true);

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");
        Executor executor = applicationContext.getBean(Executor.class);
        servers.add(executor);
        poolExecutor.execute(executor);

        if (workerControllerEnabled) {
            Controller controller = applicationContext.getBean(Controller.class);
            poolExecutor.execute(controller::start);
            servers.add(controller);
        }

        if (workerEnabled) {
            Worker worker = applicationContext.getBean(Worker.class);
            poolExecutor.execute(() -> worker.start(workerThread, null));
            servers.add(worker);
        }

        if (schedulerEnabled) {
            Scheduler scheduler = applicationContext.getBean(Scheduler.class);
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        // always start an indexer in test
        Indexer indexer = applicationContext.getBean(Indexer.class);
        poolExecutor.execute(indexer);
        servers.add(indexer);

        try {
            Await.await().atMost(getRunningTimeout()).until(() -> servers.stream().allMatch(s -> Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning()));
        } catch (ConditionTimeoutException e) {
            throw new RuntimeException(
                servers.stream().filter(s -> !Optional.ofNullable(s.getState()).orElse(Service.ServiceState.RUNNING).isRunning())
                    .map(Service::getClass)
                    .toList() + " not started in time"
            );
        }
    }

    private Duration getRunningTimeout() {
        if (serverConfig.standalone() != null && serverConfig.standalone().running() != null) {
            return serverConfig.standalone().running().timeout();
        }
        return Duration.ofMinutes(1);
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
