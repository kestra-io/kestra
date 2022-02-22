package io.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Map;

@Slf4j
public class GlobalStateLockProcessor<T> implements Processor<String, T, Void, Void> {
    private final String storeName;
    private final Map<String, T> lock;
    private KeyValueStore<String, T> store;

    public GlobalStateLockProcessor(String storeName, Map<String, T> lock) {
        this.storeName = storeName;
        this.lock = lock;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void init(ProcessorContext context) {
        this.store = (KeyValueStore<String, T>) context.getStateStore(this.storeName);
    }

    @Override
    public void process(Record<String, T> record) {
        if (record.value() == null) {
            this.store.delete(record.key());
        } else {
            this.store.put(record.key(), record.value());
        }

        this.lock.remove(record.key());
    }

    @Override
    public void close() {

    }
}
