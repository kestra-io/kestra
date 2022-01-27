package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.Executor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ExecutorNextTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final String storeName;
    private final ExecutorService executorService;
    private KeyValueStore<String, Store> store;

    public ExecutorNextTransformer(String storeName, ExecutorService executorService) {
        this.storeName = storeName;
        this.executorService = executorService;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.store = context.getStateStore(this.storeName);
    }

    @Override
    public Executor transform(final String key, final Executor value) {
        Executor executor = executorService.process(value);

        if (executor.getNexts().size() == 0) {
            return value;
        }
        Store store = this.store.get(key) == null ? new Store() : this.store.get(key);

        Map<Boolean, List<String>> groups = executor.getNexts()
            .stream()
            .map(taskRun -> taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue())
            .collect(Collectors.partitioningBy(store::contains));

        if (groups.get(true).size() > 0) {
            groups.get(true).forEach(s ->
                log.trace("Duplicate next taskRun for execution '{}', value '{}'", key, s)
            );

            return value;
        }

        store.addAll(groups.get(false));
        this.store.put(key, store);

        Execution newExecution = executorService.onNexts(
            value.getFlow(),
            value.getExecution(),
            executor.getNexts()
        );

        return value.withExecution(newExecution, "onNexts");
    }

    @Override
    public void close() {
    }

    @Getter
    @NoArgsConstructor
    public static class Store extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }
}
