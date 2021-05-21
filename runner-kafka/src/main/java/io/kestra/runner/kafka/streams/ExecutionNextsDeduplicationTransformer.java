package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ExecutionNextsDeduplicationTransformer implements ValueTransformerWithKey<String, KafkaExecutor.ExecutorWithFlow, KafkaExecutor.ExecutorWithFlow> {
    private final String storeName;
    private final AbstractExecutor abstractExecutor;
    private KeyValueStore<String, Store> store;

    public ExecutionNextsDeduplicationTransformer(String storeName, AbstractExecutor abstractExecutor) {
        this.storeName = storeName;
        this.abstractExecutor = abstractExecutor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, Store>) context.getStateStore(this.storeName);
    }

    @Override
    public KafkaExecutor.ExecutorWithFlow transform(final String key, final KafkaExecutor.ExecutorWithFlow executorWithFlow) {
        try {
            Optional<List<TaskRun>> nexts = abstractExecutor.doNexts(executorWithFlow.getExecution(), executorWithFlow.getFlow());

            if (nexts.isEmpty()) {
                return executorWithFlow;
            }

            Store store = this.store.get(key) == null ? new Store() : this.store.get(key);

            Map<Boolean, List<String>> groups = nexts.get()
                .stream()
                .map(taskRun -> taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue())
                .collect(Collectors.partitioningBy(store::contains));

            if (groups.get(true).size() > 0) {
                groups.get(true).forEach(s ->
                    log.debug("Duplicate next taskRun for execution '{}', value '{}'", key, s)
                );

                return executorWithFlow;
            }

            store.addAll(groups.get(false));
            this.store.put(key, store);

            Execution newExecution = abstractExecutor.onNexts(
                executorWithFlow.getFlow(),
                executorWithFlow.getExecution(),
                nexts.get()
            );

            return executorWithFlow.withExecution(newExecution, "ExecutionNextsDeduplicationTransformer");
        } catch (Exception e) {
            return executorWithFlow.withException(e, "ExecutionNextsDeduplicationTransformer");
        }
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
