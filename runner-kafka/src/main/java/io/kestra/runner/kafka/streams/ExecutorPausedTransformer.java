package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.Executor;
import io.kestra.core.services.ExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class ExecutorPausedTransformer implements Transformer<String, ExecutionDelay, KeyValue<String, Executor>> {
    private final String storeName;
    private final String storeExecutorName;
    private final ExecutionService executionService;

    private WindowStore<String, ExecutionDelay> store;

    private KeyValueStore<String, Executor> storeExecutor;

    public ExecutorPausedTransformer(String storeName, String storeExecutorName, ExecutionService executionService) {
        this.storeName = storeName;
        this.storeExecutorName = storeExecutorName;
        this.executionService = executionService;
    }

    @Override
    public void init(ProcessorContext context) {
        this.store = context.getStateStore(this.storeName);
        this.storeExecutor = context.getStateStore(this.storeExecutorName);

        context.schedule(Duration.ofSeconds(1), PunctuationType.WALL_CLOCK_TIME, (timestamp) -> {
            try (final KeyValueIterator<Windowed<String>, ExecutionDelay> iter = store.fetchAll(Instant.EPOCH.toEpochMilli(), timestamp)) {
                while (iter.hasNext()) {
                    final KeyValue<Windowed<String>, ExecutionDelay> entry = iter.next();

                    Executor executor = this.storeExecutor.get(entry.key.key());

                    if (executor == null) {
                        log.warn("Unable to find execution '" + entry.key.key() + "', cannot restart pause!");
                    } else if (executor.getExecution().getState().getCurrent() != State.Type.PAUSED) {
                        log.debug("Execution '" + entry.key.key() + "' is not paused (" +  executor.getExecution().getState().getCurrent()  + "), skipping!");
                    } else {
                        try {
                            Execution markAsExecution = executionService.markAs(
                                executor.getExecution(),
                                entry.value.getTaskRunId(),
                                State.Type.RUNNING
                            );

                            context.forward(
                                entry.key.key(),
                                new Executor(markAsExecution, null)
                                    .withExecution(markAsExecution, "pausedRestart")
                            );

                        } catch (Exception e) {
                            context.forward(
                                entry.key.key(),
                                new Executor(executor.getExecution(), null)
                                    .withException(e, "pausedRestart")
                            );
                        }
                    }

                    store.put(entry.key.key(), null, entry.value.getDate().toEpochMilli());
                }
            }
        });
    }

    @Override
    public KeyValue<String, Executor> transform(String key, ExecutionDelay value) {
        store.put(key, value, value.getDate().toEpochMilli());

        return null;
    }

    @Override
    public void close() {

    }
}
