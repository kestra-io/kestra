package io.kestra.core.utils;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@SuppressWarnings({"unchecked"})
@Slf4j
public class MapUtils {
    private static final String CONFLICT_AT_KEY_MSG = "Conflict at key: '{}', ignoring it. Map keys are: {}";

    /**
     * Merge map a with map b.
     * @see #deepMerge(Map, Map) that perform a deep merge which is more costly but safer for some use cases.
     */
    public static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) {
            return null;
        }

        if (a == null || a.isEmpty()) {
            return b;
        }

        if (b == null || b.isEmpty()) {
            return a;
        }

        Map<String, Object> result = HashMap.newHashMap(Math.max(a.size(), b.size()));
        result.putAll(a);

        for (Map.Entry<String, Object> entry : b.entrySet()) {
            String key = entry.getKey();
            Object valueB = entry.getValue();
            Object valueA = result.get(key);

            Object mergedValue;
            if (valueB == null) {
                mergedValue = valueA;
            } else if (valueA == null) {
                mergedValue = valueB;
            } else if (valueA instanceof Map<?, ?> mapA && valueB instanceof Map<?, ?> mapB) {
                mergedValue = merge(castMap(mapA), castMap(mapB));
            } else if (valueA instanceof Collection<?> colA && valueB instanceof Collection<?> colB) {
                mergedValue = mergeCollections(colA, colB);
            } else {
                mergedValue = valueB;
            }

            result.put(key, mergedValue);
        }

        return result;
    }

    /**
     * Merge map a with map b, deep cloning maps and lists.
     *
     * @see #merge(Map, Map) that didn't deepclone and performs better.
     */
    public static Map<String, Object> deepMerge(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) {
            return null;
        }

        if (a == null || a.isEmpty()) {
            return b;
        }

        if (b == null || b.isEmpty()) {
            return a;
        }

        Map<String, Object> result = HashMap.newHashMap(Math.max(a.size(), b.size()));
        result.putAll(deepCloneMap(a));

        for (Map.Entry<String, Object> entry : b.entrySet()) {
            String key = entry.getKey();
            Object valueB = entry.getValue();
            Object valueA = result.get(key);

            Object mergedValue;
            if (valueB == null) {
                mergedValue = valueA;
            } else if (valueA == null) {
                mergedValue = valueB;
            } else if (valueA instanceof Map<?, ?> mapA && valueB instanceof Map<?, ?> mapB) {
                mergedValue = deepMerge(castMap(mapA), castMap(mapB));
            } else if (valueA instanceof Collection<?> colA && valueB instanceof Collection<?> colB) {
                mergedValue = mergeCollections(colA, colB);
            } else {
                mergedValue = valueB;
            }

            result.put(key, mergedValue);
        }

        return result;
    }

    private static Map<String, Object> deepCloneMap(Map<String, Object> original) {
        Map<String, Object> cloned = new HashMap<>(original.size());
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            cloned.put(entry.getKey(), deepClone(entry.getValue()));
        }
        return cloned;
    }

    private static Object deepClone(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCloneMap(castMap(map));
        } else if (value instanceof Collection<?> col) {
            return cloneCollection(col);
        } else {
            return value;
        }
    }

    private static Collection<?> mergeCollections(Collection<?> colA, Collection<?> colB) {
        List<Object> merged = new ArrayList<>(colA.size() + colB.size());
        merged.addAll(colA);
        if (!colB.isEmpty()) {
            List<?> filtered = colB.stream().filter(it -> !colA.contains(it)).toList();
            merged.addAll(filtered);
        }
        return merged;
    }

    private static Collection<?> cloneCollection(Collection<?> elements) {
        try {
            Collection<Object> newInstance = elements.getClass().getDeclaredConstructor().newInstance();
            newInstance.addAll(elements);
            return newInstance;
        } catch (Exception e) {
            return new ArrayList<>(elements);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * Utility method for merging multiple {@link Map}s that can contains nullable values.
     * Note that the maps provided are assumed to be flat, so this method does not perform a recursive merge.
     *
     * @param maps  The Map to be merged.
     * @return     the merged Map.
     */
    public static Map<String, Object> mergeWithNullableValues(final Map<String, Object>...maps) {
        return Arrays.stream(maps)
            .flatMap(map -> map.entrySet().stream())
            // https://bugs.openjdk.org/browse/JDK-8148463
            .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
    }

    /**
     * Utility method that returns an empty HasMap if the <code>map</code> parameter is null,
     * the <code>map</code> parameter otherwise.
     */
    public static <K, V> Map<K, V> emptyOnNull(Map<K, V> map) {
        return map == null ? new HashMap<>() : map;
    }

    /**
     * Utility method that returns true if the map is null or empty.
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Utility method that nests a flattened map.
     *
     * @param flatMap the flattened map.
     * @return the nested map.
     *
     * @throws IllegalArgumentException if the given map contains conflicting keys.
     */
    public static Map<String, Object> flattenToNestedMap(@NotNull Map<String, ?> flatMap) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, ?> entry : flatMap.entrySet()) {
            String[] keys = entry.getKey().split("\\.");
            Map<String, Object> currentMap = result;

            for (int i = 0; i < keys.length - 1; ++i) {
                String key = keys[i];
                if (!currentMap.containsKey(key)) {
                    currentMap.put(key, new HashMap<>());
                } else if (!(currentMap.get(key) instanceof Map)) {
                    var invalidKey = String.join(",", Arrays.copyOfRange(keys, 0, i));
                    log.warn(CONFLICT_AT_KEY_MSG, invalidKey, flatMap.keySet());
                    continue;
                }
                currentMap = (Map<String, Object>) currentMap.get(key);
            }
            String lastKey = keys[keys.length - 1];
            if (currentMap.containsKey(lastKey)) {
                log.warn("Conflict at key: '{}', ignoring it. Map keys are: {}", lastKey, flatMap.keySet());
                continue;
            }
            currentMap.put(lastKey, entry.getValue());
        }
        return result;
    }

    /**
     * Utility method that flatten a nested map.
     *
     * @param nestedMap the nested map.
     * @return the flattened map.
     */
    public static Map<String, Object> nestedToFlattenMap(@NotNull Map<String, Object> nestedMap) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map) {
                Map<String, Object> flatten = flattenEntry(entry.getKey(), (Map<String, Object>) map);
                result.putAll(flatten);
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Object> flattenEntry(String key, Map<String, Object> value) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            String newKey = key + "." + entry.getKey();
            Object newValue = entry.getValue();
            if (newValue instanceof Map<?, ?> map) {
                result.putAll(flattenEntry(newKey, (Map<String, Object>) map));
            } else {
                result.put(newKey, newValue);
            }
        }

        return result;
    }
}
