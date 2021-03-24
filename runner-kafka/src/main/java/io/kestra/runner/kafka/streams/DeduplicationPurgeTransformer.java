package io.kestra.runner.kafka.streams;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DeduplicationPurgeTransformer<K, V, SV> implements ValueTransformerWithKey<K, V, V> {
    private final String storeName;
    private KeyValueStore<String, SV> store;
    private final KeyValueMapper<K, V, String> storeKeyMapper;

    public DeduplicationPurgeTransformer(
        String storeName,
        KeyValueMapper<K, V, String> storeKeyMapper
    ) {
        this.storeName = storeName;
        this.storeKeyMapper = storeKeyMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, SV>) context.getStateStore(this.storeName);
    }

    @Override
    public V transform(final K key, final V value) {
        if (value != null) {
            String storeKey = storeKeyMapper.apply(key, value);

            store.delete(storeKey);
        }

        return value;
    }

    @Override
    public void close() {
    }
}
