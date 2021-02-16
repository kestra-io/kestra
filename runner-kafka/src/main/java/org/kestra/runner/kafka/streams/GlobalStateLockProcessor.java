package org.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Map;

@Slf4j
public class GlobalStateLockProcessor<T> implements Processor<String, T> {
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
    public void process(String key, T value) {
        if (value == null) {
            this.store.delete(key);
        } else {
            this.store.put(key, value);
        }

        this.lock.remove(key);
    }

    @Override
    public void close() {

    }
}
