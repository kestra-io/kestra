package io.kestra.runner.kafka.streams;

import io.kestra.runner.kafka.services.SafeKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class GlobalInMemoryStateProcessor<T> implements Processor<String, T, Void, Void> {
    private final String storeName;
    private final Consumer<List<T>> consumer;
    private KeyValueStore<String, T> store;
    private SafeKeyValueStore<String, T> safeStore;

    public GlobalInMemoryStateProcessor(String storeName, Consumer<List<T>> consumer) {
        this.storeName = storeName;
        this.consumer = consumer;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        this.store = context.getStateStore(this.storeName);
        this.safeStore = new SafeKeyValueStore<>(this.store, this.store.name());

        this.send();
    }

    @Override
    public void process(Record<String, T> record) {
        if (record.value() == null) {
            this.store.delete(record.key());
        } else {
            this.store.put(record.key(), record.value());
        }

        this.send();
    }

    private void send() {
        consumer.accept(this.safeStore.all().collect(Collectors.toList()));
    }

    @Override
    public void close() {

    }
}
