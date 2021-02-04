package org.kestra.runner.kafka;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.internals.DelegatingPeekingKeyValueIterator;

import java.util.*;

@SuppressWarnings("SortedCollectionWithNonComparableKeys")
class MockKeyValueStore<K, V> implements KeyValueStore<K, V> {
    private final String name;
    private final NavigableMap<K, V> map = new TreeMap<>();
    private volatile boolean open = false;
    private long size = 0L; // SkipListMap#size is O(N) so we just do our best to track it

    public MockKeyValueStore(final String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void init(final ProcessorContext context,
                     final StateStore root) {
        size = 0;
        if (root != null) {
            throw new UnsupportedOperationException("init() not supported in " + getClass().getName());
        }

        open = true;
    }

    @Override
    public boolean persistent() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized V get(final K key) {
        return map.get(key);
    }

    @Override
    public synchronized void put(final K key, final V value) {
        if (value == null) {
            size -= map.remove(key) == null ? 0 : 1;
        } else {
            size += map.put(key, value) == null ? 1 : 0;
        }
    }

    @Override
    public synchronized V putIfAbsent(final K key, final V value) {
        final V originalValue = get(key);
        if (originalValue == null) {
            put(key, value);
        }
        return originalValue;
    }

    @Override
    public void putAll(final List<KeyValue<K, V>> entries) {
        for (final KeyValue<K, V> entry : entries) {
            put(entry.key, entry.value);
        }
    }

    @Override
    public synchronized V delete(final K key) {
        final V oldValue = map.remove(key);
        size -= oldValue == null ? 0 : 1;
        return oldValue;
    }

    @Override
    public synchronized KeyValueIterator<K, V> range(final K from, final K to) {
        throw new UnsupportedOperationException("range() not supported in " + getClass().getName());
    }

    @Override
    public synchronized KeyValueIterator<K, V> all() {
        return new DelegatingPeekingKeyValueIterator<>(
            name,
            new InMemoryKeyValueIterator(map.keySet())
        );
    }

    @Override
    public long approximateNumEntries() {
        return size;
    }

    @Override
    public void flush() {
        // do-nothing since it is in-memory
    }

    @Override
    public void close() {
        map.clear();
        size = 0;
        open = false;
    }

    private class InMemoryKeyValueIterator implements KeyValueIterator<K, V> {
        private final Iterator<K> iter;

        private InMemoryKeyValueIterator(final Set<K> keySet) {
            this.iter = new TreeSet<>(keySet).iterator();
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public KeyValue<K, V> next() {
            final K key = iter.next();
            return new KeyValue<>(key, map.get(key));
        }

        @Override
        public void close() {
            // do nothing
        }

        @Override
        public K peekNextKey() {
            throw new UnsupportedOperationException("peekNextKey() not supported in " + getClass().getName());
        }
    }
}
