package io.kestra.queue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Checks the contract of a queue backend when its broker restarts: a subscriber either keeps receiving
 * messages or Kestra shutdown is requested, but it never stops silently.
 * <p>
 * A backend extends it and implements {@link #restartBroker()}, usually with
 * {@link #restartDockerComposeService(String, String, int)}. A shutdown stops the application context, so a backend
 * keeps this as its only test of the class and is not transactional: {@code @MicronautTest(transactional = false)}.
 */
@Tag("integration")
public abstract class AbstractBrokerRestartQueueTest extends AbstractQueueTest {
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(90);

    @Inject
    private DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue;

    @Inject
    private BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> broadcastQueue;

    @Inject
    private QueueService queueService;

    @Inject
    private ApplicationContext applicationContext;

    private KestraContext realContext;
    private NoOpShutdownContext noOpShutdownContext;

    /**
     * Restarts the broker, returning once the restart command has completed.
     * The broker does not have to be ready again, the test waits for it.
     */
    protected abstract void restartBroker() throws Exception;

    @BeforeEach
    void swapKestraContext() {
        realContext = KestraContext.getContext();
        noOpShutdownContext = new NoOpShutdownContext(realContext, new AtomicBoolean(false));
        KestraContext.setContext(noOpShutdownContext);
        queueService.setKestraContext(noOpShutdownContext);
    }

    @AfterEach
    void restoreKestraContext() {
        KestraContext.setContext(realContext);
        queueService.setKestraContext(realContext);
    }

    @Test
    void shouldKeepReceivingOrRequestShutdownWhenBrokerRestarts() throws Exception {
        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger broadcasted = new AtomicInteger();
        QueueSubscriber<AbstractDispatchQueueTest.TestDispatch> dispatchSubscriber = dispatchQueue
            .subscriber()
            .subscribe(e -> dispatched.incrementAndGet());
        QueueSubscriber<AbstractBroadcastQueueTest.TestBroadcast> broadcastSubscriber = broadcastQueue
            .subscriber()
            .subscribe(e -> broadcasted.incrementAndGet());

        try {
            AtomicInteger id = new AtomicInteger();
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() ->
            {
                emitDispatch(id.incrementAndGet());
                emitBroadcast(id.incrementAndGet());
                return dispatched.get() >= 1 && broadcasted.get() >= 1;
            });

            int dispatchedBeforeRestart = dispatched.get();
            int broadcastedBeforeRestart = broadcasted.get();
            restartBroker();

            // the broker may still be starting, so keep emitting until a message goes through after the restart
            await().atMost(RECOVERY_TIMEOUT).pollInterval(Duration.ofSeconds(1)).until(() ->
            {
                if (isShutdownRequested()) {
                    return true;
                }
                emitDispatch(id.incrementAndGet());
                emitBroadcast(id.incrementAndGet());
                return dispatched.get() > dispatchedBeforeRestart && broadcasted.get() > broadcastedBeforeRestart;
            });

            assertThat(isShutdownRequested() || (dispatched.get() > dispatchedBeforeRestart && broadcasted.get() > broadcastedBeforeRestart))
                .as("subscribers must keep receiving after the broker restart or request a Kestra shutdown")
                .isTrue();
        } finally {
            dispatchSubscriber.close();
            broadcastSubscriber.close();
        }
    }

    /**
     * The threads of a backend shut down the context that owns them, not only the one of the queue service, so a
     * stopped application context counts as a shutdown too.
     */
    private boolean isShutdownRequested() {
        return noOpShutdownContext.isShutdownCalled() || !applicationContext.isRunning();
    }

    private boolean emitDispatch(int id) {
        try {
            dispatchQueue.emit(new AbstractDispatchQueueTest.TestDispatch(this.keyPrefix() + "_" + IdUtils.create(), id));
            return true;
        } catch (QueueException | RuntimeException e) {
            return false;
        }
    }

    private boolean emitBroadcast(int id) {
        try {
            broadcastQueue.emit(new AbstractBroadcastQueueTest.TestBroadcast(this.keyPrefix() + "_" + IdUtils.create(), id));
            return true;
        } catch (QueueException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Restarts a service of a docker compose file, relative to the working directory of the Gradle test task, and
     * returns once it is healthy, when it has a health check, and accepts connections on the port.
     */
    protected static void restartDockerComposeService(String composeFile, String service, int port) throws IOException, InterruptedException {
        runDockerCompose(composeFile, "restart", service);
        runDockerCompose(composeFile, "up", "-d", "--wait", service);

        long deadline = System.nanoTime() + RECOVERY_TIMEOUT.toNanos();
        while (!isListening(port)) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("The service '%s' of '%s' does not accept connections on the port %d.".formatted(service, composeFile, port));
            }
            Thread.sleep(500);
        }
    }

    private static boolean isListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void runDockerCompose(String composeFile, String... command) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of("docker", "compose", "-f", composeFile));
        args.addAll(List.of(command));
        Process process = new ProcessBuilder(args).inheritIO().start();
        if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("Cannot run '%s'.".formatted(String.join(" ", args)));
        }
    }
}
