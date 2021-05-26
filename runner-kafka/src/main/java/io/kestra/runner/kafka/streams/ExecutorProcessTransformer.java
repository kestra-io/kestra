package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.AbstractExecutor;
import io.kestra.runner.kafka.KafkaExecutor;
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
public class ExecutorProcessTransformer implements ValueTransformerWithKey<String, AbstractExecutor.Executor, KafkaExecutor.Executor> {
    private final String storeName;
    private final AbstractExecutor abstractExecutor;
    private KeyValueStore<String, Store> store;

    public ExecutorProcessTransformer(String storeName, AbstractExecutor abstractExecutor) {
        this.storeName = storeName;
        this.abstractExecutor = abstractExecutor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, Store>) context.getStateStore(this.storeName);
    }

    @Override
    public KafkaExecutor.Executor transform(final String key, final KafkaExecutor.Executor Executor) {
        AbstractExecutor.Executor executor = abstractExecutor.process(Executor);

        if (executor.getNexts().size() == 0) {
            return Executor;
        }
        Store store = this.store.get(key) == null ? new Store() : this.store.get(key);

        Map<Boolean, List<String>> groups = executor.getNexts()
            .stream()
            .map(taskRun -> taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue())
            .collect(Collectors.partitioningBy(store::contains));

        if (groups.get(true).size() > 0) {
            groups.get(true).forEach(s ->
                log.debug("Duplicate next taskRun for execution '{}', value '{}'", key, s)
            );

            return Executor;
        }

        store.addAll(groups.get(false));
        this.store.put(key, store);

        Execution newExecution = abstractExecutor.onNexts(
            Executor.getFlow(),
            Executor.getExecution(),
            executor.getNexts()
        );

        return Executor.withExecution(newExecution, "onNexts");
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
