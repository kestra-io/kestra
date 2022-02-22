package io.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DeduplicationTransformer<K, V, SV> implements ValueTransformerWithKey<K, V, V> {
    private final String name;
    private final String storeName;
    private KeyValueStore<String, SV> store;
    private final KeyValueMapper<K, V, String> storeKeyMapper;
    private final KeyValueMapper<K, V, SV> storeValueMapper;

    public DeduplicationTransformer(
        String name,
        String storeName,
        KeyValueMapper<K, V, String> storeKeyMapper,
        KeyValueMapper<K, V, SV> storeValueMapper
    ) {
        this.name = name;
        this.storeName = storeName;
        this.storeKeyMapper = storeKeyMapper;
        this.storeValueMapper = storeValueMapper;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.store = context.getStateStore(this.storeName);
    }

    @Override
    public V transform(final K key, final V value) {
        String storeKey = storeKeyMapper.apply(key, value);
        SV currentValue = storeValueMapper.apply(key, value);

        SV latestValue = store.get(storeKey);

        if (latestValue != null && latestValue.equals(currentValue)) {
            log.trace("{} duplicate value for key '{}', storeKey '{}', value '{}'", name, key, storeKey, latestValue);
            return null;
        }

        store.put(storeKey, currentValue);

        return value;
    }

    @Override
    public void close() {
    }
}
