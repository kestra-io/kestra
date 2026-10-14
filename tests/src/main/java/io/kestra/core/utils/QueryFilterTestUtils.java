package io.kestra.core.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kestra.core.models.QueryFilter;

/**
 * Test utility for converting {@link QueryFilter} instances to PHP-style URL query parameters
 * compatible with the format parsed by {@code QueryFilterFormatBinder}.
 *
 * <p>
 * Nested AND/OR groups are supported: each group contributes a {@code [<logical>][<index>]} segment
 * to its children's key, e.g. {@code filters[or][0][namespace][EQUALS]}.
 *
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * Map<String, String> params = QueryFilterTestUtils.toQueryParams(filters);
 * UriBuilder builder = UriBuilder.of("/api/v1/executions/search");
 * params.forEach(builder::queryParam);
 * }</pre>
 */
public final class QueryFilterTestUtils {

    private QueryFilterTestUtils() {
    }

    public static Map<String, String> toQueryParams(List<QueryFilter> filters) {
        Map<String, String> result = new LinkedHashMap<>();
        serializeFilters("filters", filters, result);
        return result;
    }

    private static void serializeFilters(String prefix, List<QueryFilter> filters, Map<String, String> result) {
        for (QueryFilter filter : filters) {
            serializeFilter(prefix, filter, result);
        }
    }

    private static void serializeFilter(String prefix, QueryFilter filter, Map<String, String> result) {
        if (filter.isNode()) {
            List<QueryFilter> children = filter.children();
            for (int i = 0; i < children.size(); i++) {
                serializeFilter(
                    "%s[%s][%d]".formatted(prefix, filter.logical().value(), i),
                    children.get(i),
                    result
                );
            }
            return;
        }

        String baseKey = "%s[%s][%s]".formatted(
            prefix,
            filter.field().value(),
            filter.operation().name()
        );
        serializeValue(baseKey, filter.value(), result);
    }

    @SuppressWarnings("unchecked")
    private static void serializeValue(String baseKey, Object value, Map<String, String> result) {
        if (value instanceof List<?> list) {
            result.put(baseKey, list.stream().map(String::valueOf).collect(Collectors.joining(",")));
        } else if (value instanceof Map<?, ?> map) {
            ((Map<String, String>) map).forEach((k, v) -> result.put(baseKey + "[" + k + "]", v));
        } else {
            result.put(baseKey, String.valueOf(value));
        }
    }
}
