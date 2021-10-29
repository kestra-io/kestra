package io.kestra.runner.kafka.services;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
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

    @SuppressWarnings("UnstableApiUsage")
    public Stream<V> toStream() {
        KeyValueIterator<K, V> all = this.store.all();

        return Streams
            .stream(new Iterator<V>() {
                private V next;

                @Override
                public boolean hasNext() {
                     boolean seek = true;
                     while (seek) {
                         try {
                             next = all.next().value;

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
                public V next() {
                    return this.next;
                }
            })
            .onClose(all::close);
    }
}
