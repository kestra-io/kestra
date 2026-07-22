package io.kestra.core.runners;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.core.ConditionTimeoutException;

import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.worker.Controller;
import io.kestra.worker.systemworker.SystemWorker;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
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
    @Setter
    private boolean systemWorkerEnabled = false;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private ApplicationContext applicationContext;

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
            poolExecutor.execute(() -> worker.start(workerThread));
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

        // Opt-in: SystemWorker is only started when the test explicitly
        // requests it via @KestraTest(startSystemWorker = true). Off by default
        // so test runs that don't exercise SystemTasks don't pay for the
        // SystemWorker's thread pool and queue subscriptions.
        if (systemWorkerEnabled) {
            systemWorkerProvider.ifPresent(worker ->
            {
                poolExecutor.execute(worker::start);
                servers.add(worker);
            });
        }

        try {
            Await.await().atMost(getRunningTimeout()).until(
                () -> servers.stream().allMatch(TestRunner::isStarted) || servers.stream().anyMatch(TestRunner::hasFailedToStart)
            );
        } catch (ConditionTimeoutException e) {
            throw new RuntimeException(
                servers.stream().filter(s -> !isStarted(s))
                    .map(s -> s.getClass().getSimpleName() + " (state: " + s.getState() + ")")
                    .toList() + " not started in time"
            );
        }

        List<Service> failed = servers.stream().filter(TestRunner::hasFailedToStart).toList();
        if (!failed.isEmpty()) {
            throw new RuntimeException(
                failed.stream()
                    .map(s -> s.getClass().getSimpleName() + " (state: " + s.getState() + ")")
                    .toList() + " terminated during startup: the context was most likely shut down by an uncaught exception, check the logs above for the root cause"
            );
        }
    }

    /**
     * A service is only considered started once it reached RUNNING (or MAINTENANCE).
     */
    private static boolean isStarted(Service service) {
        Service.ServiceState state = service.getState();
        return Service.ServiceState.RUNNING == state || Service.ServiceState.MAINTENANCE == state;
    }

    /**
     * A service can never (re)reach RUNNING once it left the CREATED, RUNNING and MAINTENANCE
     * states: seeing any other state during startup means the service — usually the whole
     * context — was shut down underneath us, so keeping on waiting would only time out and
     * hide the root cause. A {@code null} state means the service has not registered yet and
     * is still starting.
     */
    private static boolean hasFailedToStart(Service service) {
        Service.ServiceState state = service.getState();
        return state != null
            && Service.ServiceState.CREATED != state
            && Service.ServiceState.RUNNING != state
            && Service.ServiceState.MAINTENANCE != state;
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
