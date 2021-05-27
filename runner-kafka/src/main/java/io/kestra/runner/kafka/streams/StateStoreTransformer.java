package io.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.function.Function;

@Slf4j
public class StateStoreTransformer<V> implements ValueTransformerWithKey<String, V, V> {
    private final Function<V, V> serializer;
    private final String storeName;
    private KeyValueStore<String, V> store;

    public StateStoreTransformer(String storeName, Function<V, V> serializer) {
        this.storeName = storeName;
        this.serializer = serializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, V>) context.getStateStore(this.storeName);
    }

    @Override
    public V transform(final String key, final V value) {
        if (value == null) {
            store.delete(key);
            if (log.isTraceEnabled()) {
                log.trace("Delete State Store with key '" + key + "'");
            }
        } else {
            store.put(key, serializer.apply(value));
            if (log.isTraceEnabled()) {
                log.trace("State Store save from '" + value.getClass().getSimpleName() + "' with key '" + key + "'");
            }
        }

        return value;
    }

    @Override
    public void close() {
    }
}
