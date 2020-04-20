package org.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DeduplicationTransformer<K, V, SV> implements Transformer<K, V, KeyValue<K, V>> {
    private final String storeName;
    private KeyValueStore<String, SV> store;
    private final KeyValueMapper<K, V, String> storeKeyMapper;
    private final KeyValueMapper<K, V, SV> storeValueMapper;

    public DeduplicationTransformer(
        String storeName,
        KeyValueMapper<K, V, String> storeKeyMapper,
        KeyValueMapper<K, V, SV> storeValueMapper
    ) {
        this.storeName = storeName;
        this.storeKeyMapper = storeKeyMapper;
        this.storeValueMapper = storeValueMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, SV>) context.getStateStore(this.storeName);
    }

    @Override
    public KeyValue<K, V> transform(final K key, final V value) {
        String storeKey = storeKeyMapper.apply(key, value);
        SV currentValue = storeValueMapper.apply(key, value);

        SV latestValue = store.get(storeKey);

        if (latestValue != null && latestValue.equals(currentValue)) {
            log.info("Duplicate value");
            return null;
        }

        store.put(storeKey, currentValue);

        return KeyValue.pair(key, value);
    }

    @Override
    public void close() {
    }
}
