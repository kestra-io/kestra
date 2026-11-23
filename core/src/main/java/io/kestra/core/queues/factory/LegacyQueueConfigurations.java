package io.kestra.core.queues.factory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.value.PropertyResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps the properties declared through {@link LegacyQueueConfiguration} on a queue factory into its
 * {@code kestra.queue.<id>} configuration.
 */
@Slf4j
public final class LegacyQueueConfigurations {

    private LegacyQueueConfigurations() {
    }

    /**
     * @return the plugin configuration completed with the legacy properties, the configured values winning.
     */
    public static Map<String, Object> apply(Class<?> pluginClass, String pluginId, Map<String, Object> pluginConfiguration, PropertyResolver propertyResolver) {
        Map<String, Object> merged = new LinkedHashMap<>(pluginConfiguration);
        for (LegacyQueueConfiguration legacy : pluginClass.getAnnotationsByType(LegacyQueueConfiguration.class)) {
            // CAMEL_CASE reads the generated catalog, the only one that also holds environment variables
            Map<String, Object> flat = propertyResolver.getProperties(legacy.prefix(), StringConvention.CAMEL_CASE);
            if (flat.isEmpty()) {
                continue;
            }

            String target = QueuePluginInterfaceFactory.KESTRA_QUEUE_CONFIG + "." + pluginId + (legacy.path().isEmpty() ? "" : "." + legacy.path());
            log.warn("The configuration under '{}' is deprecated, move it to '{}'.", legacy.prefix(), target);

            Map<String, Object> nested = new LinkedHashMap<>();
            flat.forEach((key, value) -> put(nested, normalize(key), value));

            Map<String, Object> legacyAtRoot = nested;
            String[] path = legacy.path().isEmpty() ? new String[0] : legacy.path().split("\\.");
            for (int i = path.length - 1; i >= 0; i--) {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put(StringConvention.CAMEL_CASE.format(path[i]), legacyAtRoot);
                legacyAtRoot = wrapper;
            }
            merged = merge(legacyAtRoot, merged);
        }
        return merged;
    }

    private static String normalize(String dottedKey) {
        return Arrays.stream(dottedKey.split("\\.")).map(StringConvention.CAMEL_CASE::format).collect(Collectors.joining("."));
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> target, String dottedKey, Object value) {
        String[] segments = dottedKey.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < segments.length - 1; i++) {
            Object child = current.computeIfAbsent(segments[i], k -> new LinkedHashMap<>());
            // a scalar already bound at this segment wins, as in Micronaut's own nested binding
            if (!(child instanceof Map<?, ?>)) {
                return;
            }
            current = (Map<String, Object>) child;
        }
        current.put(segments[segments.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        override.forEach((key, value) -> {
            if (value instanceof Map<?, ?> overrideMap && result.get(key) instanceof Map<?, ?> baseMap) {
                result.put(key, merge((Map<String, Object>) baseMap, (Map<String, Object>) overrideMap));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }
}
