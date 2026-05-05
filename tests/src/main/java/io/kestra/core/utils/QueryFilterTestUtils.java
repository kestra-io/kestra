package io.kestra.core.utils;

import io.kestra.core.models.QueryFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test utility for converting {@link QueryFilter} instances to PHP-style URL query parameters
 * compatible with the format parsed by {@code QueryFilterFormatBinder}.
 *
 * <p>Example usage:
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
        for (QueryFilter filter : filters) {
            String baseKey = "filters[%s][%s]".formatted(
                filter.field().value(),
                filter.operation().name()
            );
            serializeValue(baseKey, filter.value(), result);
        }
        return result;
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
