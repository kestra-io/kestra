package io.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class GlobalStateProcessor <T> implements Processor<String, T> {
    private final String storeName;
    private KeyValueStore<String, T> store;

    public GlobalStateProcessor(String storeName) {
        this.storeName = storeName;
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
    }

    @Override
    public void close() {

    }
}
