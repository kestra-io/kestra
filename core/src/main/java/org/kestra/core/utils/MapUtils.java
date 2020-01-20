package org.kestra.core.utils;

import java.util.*;

public class MapUtils {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        HashMap<String, Object> result = new HashMap<>(a);

        for (Map.Entry<String, Object> entry : b.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                Object current = result.get(entry.getKey());

                if (current instanceof Map) {
                    result.put(entry.getKey(), merge((Map<String, Object>) current, (Map<String, Object>) entry.getValue()));
                } else {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }
}
