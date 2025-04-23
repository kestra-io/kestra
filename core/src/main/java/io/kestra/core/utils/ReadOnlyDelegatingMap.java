package io.kestra.core.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A read-only maps that delegate all method calls to a delegate map.
 */
public abstract class ReadOnlyDelegatingMap<K, V> implements Map<K, V> {

    protected abstract Map<K, V> getDelegate();

    @Override
    public int size() {
        return getDelegate().size();
    }

    @Override
    public boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return getDelegate().get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException("This map is read-only");
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("This map is read-only");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("This map is read-only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("This map is read-only");
    }

    @Override
    public Set<K> keySet() {
        return getDelegate().keySet();
    }

    @Override
    public Collection<V> values() {
        return getDelegate().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return getDelegate().entrySet();
    }
}
