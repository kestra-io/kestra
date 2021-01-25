package org.kestra.runner.kafka.streams;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.kestra.runner.kafka.KafkaExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ExecutionNextsDeduplicationTransformer implements ValueTransformerWithKey<String, KafkaExecutor.ExecutionNexts, KafkaExecutor.ExecutionNexts> {
    private final String storeName;
    private KeyValueStore<String, Store> store;

    public ExecutionNextsDeduplicationTransformer(String storeName) {
        this.storeName = storeName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, Store>) context.getStateStore(this.storeName);
    }

    @Override
    public KafkaExecutor.ExecutionNexts transform(final String key, final KafkaExecutor.ExecutionNexts value) {
        if (value.getNexts() == null) {
            return null;
        }

        Store store = this.store.get(key) == null ? new Store() : this.store.get(key);

        Map<Boolean, List<String>> groups = value.getNexts()
            .stream()
            .map(taskRun -> taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue())
            .collect(Collectors.partitioningBy(store::contains));

        if (groups.get(true).size() > 0) {
            groups.get(true).forEach(s ->
                log.debug("Duplicate next taskRun for execution '{}', value '{}'", key, s)
            );

            return null;
        }

        store.addAll(groups.get(false));
        this.store.put(key, store);

        return value;
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
