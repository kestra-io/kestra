package io.kestra.core.utils;

import com.google.common.collect.Lists;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MapUtils {
    public static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null)
            return null;
        if (a == null || a.size() == 0)
            return copyMap(b);
        if (b == null || b.size() == 0)
            return copyMap(a);
        Map copy = copyMap(a);

        copy.putAll(
            b
                .keySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        key -> key,
                        key -> {
                            Object original = copy.get(key);
                            Object value = b.get(key);
                            if (value == null && original == null)
                                return null;
                            if (value == null)
                                return original;
                            if (original == null)
                                return value;
                            if (value instanceof Map && original instanceof Map)
                                return merge((Map) original, (Map) value);
                            else if (value instanceof Collection
                                && original instanceof Collection) {
                                try {
                                    Collection merge =
                                        copyCollection(
                                            (Collection) original,
                                            (List) Lists
                                                .newArrayList(
                                                    (Collection) original,
                                                    (Collection) value
                                                )
                                                .stream()
                                                .flatMap(Collection::stream)
                                                .collect(Collectors.toList())
                                        );
                                    return merge;
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            return value;
                        }
                    )));
        return copy;
    }

    private static Map copyMap(Map original) {
        return (Map) original
            .keySet()
            .stream()
            .collect(
                Collectors.toMap(
                    key -> key,
                    key -> {
                        Object value = original.get(key);
                        if (value instanceof Map)
                            return copyMap((Map) value);
                        if (value instanceof Collection)
                            return copyCollection((Collection) value, (Collection) value);
                        return value;
                    }
                ));
    }

    private static Collection copyCollection(Collection collection, Collection elements) {
        try {
            Collection newInstance = collection.getClass().getDeclaredConstructor().newInstance();
            newInstance.addAll(elements);
            return newInstance;
        } catch (Exception e) {
            return new ArrayList<>(elements);
        }
    }
}
