package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.services.TaskOutputService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class LazyOutputsMap implements Map<String, Object> {
    private final LazyOutputProvider provider;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile boolean allLoaded = false;
    private volatile Set<String> taskIdsWithOutputs = null;

    /**
     * Create a LazyOutputsMap backed by the given {@link LazyOutputProvider}.
     */
    public LazyOutputsMap(LazyOutputProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    /**
     * Backward-compatible constructor that wraps a {@link TaskOutputService} and {@link Execution}
     * into a {@link DefaultLazyOutputProvider}.
     */
    public LazyOutputsMap(TaskOutputService taskOutputService, Execution execution) {
        this(new DefaultLazyOutputProvider(taskOutputService, execution));
    }

    private void loadAll() {
        if (!allLoaded) {
            synchronized (this) {
                if (!allLoaded) {
                    Map<String, Object> all = provider.computeOutputs();
                    if (all != null) {
                        cache.putAll(all);
                    }
                    allLoaded = true;
                }
            }
        }
    }

    @Override
    public int size() {
        return taskIdsWithOutputs().size();
    }

    @Override
    public boolean isEmpty() {
        return taskIdsWithOutputs().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }
        if (cache.containsKey(key)) {
            return true;
        }
        if (key instanceof String taskId) {
            Set<String> ids = taskIdsWithOutputs();
            return ids.contains(taskId) || provider.valueToTaskIds().containsKey(taskId);
        }

        // non-string keys: fallback to loading all
        loadAll();
        return cache.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        loadAll();
        return cache.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        if (key == null) {
            return null;
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        if (!(key instanceof String taskId)) {
            // non-string keys are not supported for lazy per-task outputs; load all to be safe
            loadAll();
            return cache.get(key);
        }

        return getOrFetchOutput(taskId);
    }

    @Override
    public Object put(String key, Object value) {
        return cache.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return cache.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        cache.putAll(m);
    }

    @Override
    public void clear() {
        cache.clear();
        allLoaded = false;
    }

    @Override
    public Set<String> keySet() {
        return taskIdsWithOutputs();
    }

    @Override
    public Collection<Object> values() {
        List<Object> values = new ArrayList<>();
        for (String taskId : taskIdsWithOutputs()) {
            values.add(getOrFetchOutput(taskId));
        }
        return values;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entries = new LinkedHashSet<>();
        for (String taskId : taskIdsWithOutputs()) {
            entries.add(Map.entry(taskId, getOrFetchOutput(taskId)));
        }
        return entries;
    }

    private Object getOrFetchOutput(String taskId) {
        if (cache.containsKey(taskId)) {
            return cache.get(taskId);
        }
        Map<String, Object> value = provider.computeOutputsForTask(taskId);
        Map<String, Object> toCache = value == null ? Map.of() : value;
        cache.put(taskId, toCache);
        return toCache;
    }

    private Set<String> taskIdsWithOutputs() {
        if (taskIdsWithOutputs == null) {
            synchronized (this) {
                if (taskIdsWithOutputs == null) {
                    Set<String> ids = provider.findTaskIdsWithOutput();
                    taskIdsWithOutputs = ids == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
                }
            }
        }
        return taskIdsWithOutputs;
    }
}
