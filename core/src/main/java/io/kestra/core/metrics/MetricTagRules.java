package io.kestra.core.metrics;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;

/**
 * Tag rules read from {@code kestra.metrics}: {@code all} applies to every metric, any other key is a metric name
 * with its own {@code excluded-tags} and/or {@code included-tags}. Tags are glob patterns matched against whole tag keys.
 * A metric with its own rule ignores {@code all}:
 * <ul>
 * <li>{@code excluded-tags} drops the matching tags.</li>
 * <li>{@code included-tags} is a whitelist: only the matching tags are kept. Combined with {@code excluded-tags}, the latter wins.</li>
 * </ul>
 */
public final class MetricTagRules {
    public static final MetricTagRules NONE = new MetricTagRules(null, Rule.EMPTY, Map.of());

    private static final String ALL = "all";
    private static final String EXCLUDED_TAGS = "excluded-tags";
    private static final String INCLUDED_TAGS = "included-tags";

    private final String prefix;
    private final Rule all;
    private final Map<String, Rule> byMetric;
    private final Map<CacheKey, Boolean> cache = new ConcurrentHashMap<>();

    private MetricTagRules(String prefix, Rule all, Map<String, Rule> byMetric) {
        this.prefix = prefix;
        this.all = all;
        this.byMetric = byMetric;
    }

    public static MetricTagRules fromEnvironment(Environment environment) {
        return environment.getProperty("kestra.metrics", Argument.mapOf(String.class, Object.class))
            .map(MetricTagRules::fromProperties)
            .orElse(NONE);
    }

    /**
     * @param properties the nested {@code kestra.metrics} properties, e.g. {@code all -> excluded-tags -> [flow_id]}
     */
    public static MetricTagRules fromProperties(Map<String, Object> properties) {
        Map<String, List<String>> excluded = new HashMap<>();
        Map<String, List<String>> included = new HashMap<>();
        collect("", properties, excluded, included);

        if (excluded.isEmpty() && included.isEmpty()) {
            return NONE;
        }

        Map<String, Rule> byMetric = new HashMap<>();
        excluded.keySet().forEach(name -> byMetric.put(name, rule(excluded.get(name), included.get(name))));
        included.keySet().forEach(name -> byMetric.putIfAbsent(name, rule(excluded.get(name), included.get(name))));
        Rule all = byMetric.remove(ALL);

        Object prefix = properties.get("prefix");
        return new MetricTagRules(prefix == null ? null : prefix.toString(), all == null ? Rule.EMPTY : all, byMetric);
    }

    /**
     * @param metricName the metric name, with or without the configured prefix
     */
    public boolean isExcluded(String metricName, String tagKey) {
        if (this == NONE) {
            return false;
        }
        return cache.computeIfAbsent(new CacheKey(metricName, tagKey), k -> evaluate(k.metricName(), k.tagKey()));
    }

    private boolean evaluate(String metricName, String tagKey) {
        Path key = Path.of(tagKey);
        String name = prefix != null && metricName.startsWith(prefix + ".") ? metricName.substring(prefix.length() + 1) : metricName;
        Rule metric = byMetric.get(name);

        if (metric == null) {
            return matches(all.excluded(), key);
        }

        return matches(metric.excluded(), key) || (!metric.included().isEmpty() && !matches(metric.included(), key));
    }

    private static boolean matches(List<PathMatcher> matchers, Path key) {
        return matchers.stream().anyMatch(matcher -> matcher.matches(key));
    }

    @SuppressWarnings("unchecked")
    private static void collect(String path, Map<?, ?> node, Map<String, List<String>> excluded, Map<String, List<String>> included) {
        node.forEach((rawKey, value) ->
        {
            String key = String.valueOf(rawKey);
            if (key.contains("[")) {
                return;
            }
            if (EXCLUDED_TAGS.equals(key) || INCLUDED_TAGS.equals(key)) {
                (EXCLUDED_TAGS.equals(key) ? excluded : included).put(path, patterns(value));
            } else if (value instanceof Map<?, ?> child) {
                collect(path.isEmpty() ? key : path + "." + key, child, excluded, included);
            }
        });
    }

    private static List<String> patterns(Object value) {
        List<String> patterns = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> patterns.add(String.valueOf(item).trim()));
        } else if (value != null) {
            for (String item : String.valueOf(value).split(",")) {
                patterns.add(item.trim());
            }
        }
        return patterns;
    }

    private static Rule rule(List<String> excluded, List<String> included) {
        return new Rule(matchers(excluded), matchers(included));
    }

    private static List<PathMatcher> matchers(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream().map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern)).toList();
    }

    private record Rule(List<PathMatcher> excluded, List<PathMatcher> included) {
        static final Rule EMPTY = new Rule(List.of(), List.of());
    }

    private record CacheKey(String metricName, String tagKey) {
    }
}
