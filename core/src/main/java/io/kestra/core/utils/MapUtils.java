package io.kestra.core.utils;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
@Slf4j
public class MapUtils {
    private static final String CONFLICT_AT_KEY_MSG = "Conflict at key: '{}', ignoring it. Map keys are: {}";

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

        Map copy = copyMap(a);

        Map<String, Object> copyMap = b
            .entrySet()
            .stream()
            .collect(
                () -> HashMap.newHashMap(copy.size()),
                (m, v) -> {
                    Object original = copy.get(v.getKey());
                    Object value = v.getValue();
                    Object found;

                    if (value == null) {
                        found = original;
                    } else if (original == null) {
                        found = value;
                    } else if (value instanceof Map mapValue && original instanceof Map mapOriginal) {
                        found = merge(mapOriginal, mapValue);
                    } else if (value instanceof Collection collectionValue
                        && original instanceof Collection collectionOriginal) {
                        found = mergeCollections(collectionOriginal, collectionValue);
                    } else {
                        found = value;
                    }

                    m.put(v.getKey(), found);
                },
                HashMap::putAll
            );

        copy.putAll(copyMap);

        return copy;
    }

    private static Collection mergeCollections(Collection collectionOriginal, Collection collectionValue) {
        List<?> newList = new ArrayList<>(collectionOriginal.size() + collectionValue.size());
        newList.addAll(collectionOriginal);
        newList.addAll(collectionValue);
        return newList;
    }

    private static Map copyMap(Map original) {
        return ((Map<?, ?>) original)
            .entrySet()
            .stream()
            .collect(
                () -> HashMap.newHashMap(original.size()),
                (map, entry) -> {
                    Object value = entry.getValue();
                    Object found;

                    if (value instanceof Map mapValue) {
                        found = cloneMap(mapValue);
                    } else if (value instanceof Collection collectionValue) {
                        found = cloneCollection(collectionValue);
                    } else {
                        found = value;
                    }

                    map.put(entry.getKey(), found);

                },
                Map::putAll
            );
    }

    private static Map cloneMap(Map elements) {
        try {
            Map newInstance = elements.getClass().getDeclaredConstructor().newInstance();
            newInstance.putAll(elements);
            return newInstance;
        } catch (Exception e) {
            return new HashMap(elements);
        }
    }

    private static Collection cloneCollection(Collection elements) {
        try {
            Collection newInstance = elements.getClass().getDeclaredConstructor().newInstance();
            newInstance.addAll(elements);
            return newInstance;
        } catch (Exception e) {
            return new ArrayList<>(elements);
        }
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
     * Utility method nested a flattened map.
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
                    log.error(CONFLICT_AT_KEY_MSG, invalidKey, flatMap.keySet());
                }
                currentMap = (Map<String, Object>) currentMap.get(key);
            }
            String lastKey = keys[keys.length - 1];
            if (currentMap.containsKey(lastKey)) {
                log.error("Conflict at key: '{}', ignoring it. Map keys are: {}", lastKey, flatMap.keySet());
            }
            currentMap.put(lastKey, entry.getValue());
        }
        return result;
    }
}
