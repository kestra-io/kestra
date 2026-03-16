package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.TaskOutputService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class LazyOutputsMap implements Map<String, Object> {
    private final TaskOutputService taskOutputService;
    private final Execution execution;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile boolean allLoaded = false;
    private volatile Set<String> taskIdsWithOutputs = null;
    private final Map<String, List<String>> valueToTaskIds;

    public LazyOutputsMap(TaskOutputService taskOutputService, Execution execution) {
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.execution = execution;
        Set<String> taskIdSet;
        if (execution != null && execution.getTaskRunList() != null) {

            Map<String, List<String>> vmap = new HashMap<>();
            for (TaskRun tr : execution.getTaskRunList()) {
                if (tr.getValue() != null) {
                    vmap.computeIfAbsent(tr.getValue(), k -> new ArrayList<>()).add(tr.getTaskId());
                }
            }
            this.valueToTaskIds = Map.copyOf(vmap);
        } else {
            this.valueToTaskIds = Collections.emptyMap();
        }
    }

    private void loadAll() {
        if (!allLoaded) {
            synchronized (this) {
                if (!allLoaded) {
                    Map<String, Object> all = taskOutputService.computeOutputs(execution);
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
        if (execution != null && execution.getTaskRunList() != null) {
            return taskIdsWithOutputs().size();
        }

        loadAll();
        return cache.size();
    }

    @Override
    public boolean isEmpty() {
        if (execution != null && execution.getTaskRunList() != null) {
            return taskIdsWithOutputs().isEmpty();
        }

        loadAll();
        return cache.isEmpty();
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
            if (execution != null && execution.getTaskRunList() != null) {
                Set<String> ids = taskIdsWithOutputs();
                if (!ids.contains(taskId) && !valueToTaskIds.containsKey(taskId)) {
                    return false;
                }
                return true;
            } else {
                loadAll();
                return cache.containsKey(key);
            }
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

        if (!(key instanceof String)) {
            // non-string keys are not supported for lazy per-task outputs; load all to be safe
            loadAll();
            return cache.get(key);
        }

        String taskId = (String) key;
        Map<String, Object> value = taskOutputService.computeOutputsForTask(execution, taskId);
        Map<String, Object> toCache = value == null ? Map.of() : value;
        cache.put(taskId, toCache);
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) cache.get(taskId);
        return cached;
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
        if (execution != null && execution.getTaskRunList() != null) {
            return taskIdsWithOutputs();
        }

        loadAll();
        return cache.keySet();
    }

    @Override
    public Collection<Object> values() {
        if (execution != null && execution.getTaskRunList() != null) {
            List<Object> values = new ArrayList<>();
            for (String taskId : taskIdsWithOutputs()) {
                if (cache.containsKey(taskId)) {
                    values.add(cache.get(taskId));
                } else {
                    Map<String, Object> value = taskOutputService.computeOutputsForTask(execution, taskId);
                    Map<String, Object> toCache = value == null ? Map.of() : value;
                    cache.put(taskId, toCache);
                    values.add(toCache);
                }
            }
            return values;
        }

        loadAll();
        return cache.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        if (execution != null && execution.getTaskRunList() != null) {
            Set<Entry<String, Object>> entries = new LinkedHashSet<>();
            for (String taskId : taskIdsWithOutputs()) {
                Object value;
                if (cache.containsKey(taskId)) {
                    value = cache.get(taskId);
                } else {
                    Map<String, Object> v = taskOutputService.computeOutputsForTask(execution, taskId);
                    Map<String, Object> toCache = v == null ? Map.of() : v;
                    cache.put(taskId, toCache);
                    value = toCache;
                }
                entries.add(Map.entry(taskId, value));
            }
            return entries;
        }

        loadAll();
        return cache.entrySet();
    }

    private Set<String> taskIdsWithOutputs() {
        if (taskIdsWithOutputs == null) {
            synchronized (this) {
                if (taskIdsWithOutputs == null) {
                    Set<String> ids = taskOutputService.findTaskIdWithOutputByExecution(execution);
                    taskIdsWithOutputs = ids == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
                }
            }
        }
        return taskIdsWithOutputs;
    }

//    public Map<String, Object> asMap() {
//        loadAll();
//        return Map.copyOf(new HashMap<>(cache));
//    }
}
