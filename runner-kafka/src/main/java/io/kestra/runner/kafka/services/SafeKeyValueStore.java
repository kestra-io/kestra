package io.kestra.runner.kafka.services;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class SafeKeyValueStore <K, V> {
    private final ReadOnlyKeyValueStore<K, V> store;
    private final String name;

    public SafeKeyValueStore(ReadOnlyKeyValueStore<K, V> store, String name) {
        this.store = store;
        this.name = name;
    }

    public Optional<V> get(K key) {
        try {
            return Optional.ofNullable(this.store.get(key));
        } catch (SerializationException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception on store {}", name, e);
            }
            return Optional.empty();
        }
    }

    public Stream<V> all() {
        KeyValueIterator<K, V> all = this.store.all();

        return toStream(all, kvKeyValue -> kvKeyValue.value);
    }

    public Stream<KeyValue<K, V>> allWithKey() {
        KeyValueIterator<K, V> all = this.store.all();

        return toStream(all, kvKeyValue -> new KeyValue<>(kvKeyValue.key, kvKeyValue.value));
    }

    public Stream<V> prefix(String prefix) {
        KeyValueIterator<K, V> all = this.store.prefixScan(prefix, new StringSerializer());

        return toStream(all, kvKeyValue -> kvKeyValue.value);
    }

    public Stream<KeyValue<K, V>> prefixWithKey(String prefix) {
        KeyValueIterator<K, V> all = this.store.prefixScan(prefix, new StringSerializer());

        return toStream(all, kvKeyValue -> new KeyValue<>(kvKeyValue.key, kvKeyValue.value));
    }

    @SuppressWarnings("UnstableApiUsage")
    private <R> Stream<R> toStream(KeyValueIterator<K, V> all, Function<KeyValue<K, V>, R> function) {
        return Streams
            .stream(new Iterator<R>() {
                private R next;

                @Override
                public boolean hasNext() {
                    boolean seek = true;
                    while (seek) {
                        try {
                            KeyValue<K, V> rawNext = all.next();

                            next = function.apply(rawNext);

                            return true;
                        } catch (SerializationException e) {
                            if (log.isTraceEnabled()) {
                                log.trace("Exception on store {}", name, e);
                            }
                        } catch (NoSuchElementException e) {
                            seek = false;
                        }
                    }

                    all.close();
                    return false;
                }

                @Override
                public R next() {
                    return this.next;
                }
            })
            .onClose(all::close);
    }
}
