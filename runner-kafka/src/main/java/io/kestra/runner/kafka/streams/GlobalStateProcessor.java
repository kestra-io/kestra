package io.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class GlobalStateProcessor <T> implements Processor<String, T, Void, Void> {
    private final String storeName;
    private final Consumer<List<T>> consumer;
    private KeyValueStore<String, T> store;

    public GlobalStateProcessor(String storeName) {
        this(storeName, null);
    }

    public GlobalStateProcessor(String storeName, Consumer<List<T>> consumer) {
        this.storeName = storeName;
        this.consumer = consumer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void init(ProcessorContext context) {
        this.store = (KeyValueStore<String, T>) context.getStateStore(this.storeName);

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

    @SuppressWarnings("UnstableApiUsage")
    private void send() {
        if (consumer != null) {
            try (KeyValueIterator<String, T> all = this.store.all()) {
                consumer.accept(Streams.stream(all).map(e -> e.value).collect(Collectors.toList()));
            }
        }
    }

    @Override
    public void close() {

    }
}
