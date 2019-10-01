package org.floworc.runner.memory;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.queues.QueueInterface;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class MemoryQueue<T> implements QueueInterface<T> {
    private Class<T> cls;
    // private static ExecutorService poolExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static int threads = Runtime.getRuntime().availableProcessors();
    private static ExecutorService poolExecutor = new ThreadPoolExecutor(
        threads,
        threads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.info("sdfsdf");
            }
        }
    );

    private Map<String, List<Consumer<T>>> consumers = new HashMap<>();

    public MemoryQueue(Class<T> cls) {
        this.cls = cls;
    }

    @Override
    public void emit(T message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        this.consumers
            .forEach((consumerGroup, consumers) -> {
                poolExecutor.execute(() -> {
                    consumers.get((new Random()).nextInt(consumers.size())).accept(message);
                });
            });
    }

    @Override
    public synchronized Runnable receive(Class consumerGroup, Consumer<T> consumer) {
        if (!this.consumers.containsKey(consumerGroup.getName())) {
            this.consumers.put(consumerGroup.getName(), new ArrayList<>());
        }

        synchronized (this) {
            this.consumers.get(consumerGroup.getName()).add(consumer);
            int index = this.consumers.get(consumerGroup.getName()).size() - 1;

            return () -> {
                this.consumers.get(consumerGroup.getName()).remove(index);
            };
        }
    }

    public int getSubscribersCount() {
        return this.consumers
            .values()
            .stream()
            .map(List::size)
            .reduce(0, Integer::sum);
    }

    @Override
    public void close() throws IOException {
        if (!poolExecutor.isShutdown()) {
            poolExecutor.shutdown();
        }
    }
}
