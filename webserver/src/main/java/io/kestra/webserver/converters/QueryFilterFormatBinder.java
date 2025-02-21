package io.kestra.webserver.converters;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import jakarta.inject.Singleton;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class QueryFilterFormatBinder implements AnnotatedRequestArgumentBinder<QueryFilterFormat, List<QueryFilter>> {

    private static final Pattern FILTER_PATTERN = Pattern.compile("filters\\[(.*?)]\\[(.*?)](?:\\[(.+)])?");

    @VisibleForTesting
    static List<QueryFilter> getQueryFilters(Map<String, List<String>> queryParams) {
        List<QueryFilter> filters = new ArrayList<>();

        queryParams.forEach((key, values) -> {
            if (!key.startsWith("filters[")) return;

            Matcher matcher = FILTER_PATTERN.matcher(key);

            if (matcher.matches()) {
                parseFilters(values, matcher, filters);
            }
        });

        return filters;
    }

    @Override
    public Class<QueryFilterFormat> getAnnotationType() {
        return QueryFilterFormat.class;
    }

    @Override
    public BindingResult<List<QueryFilter>> bind(ArgumentConversionContext<List<QueryFilter>> context,
                                                 HttpRequest<?> source) {
        Map<String, List<String>> queryParams = source.getParameters().asMap();
        List<QueryFilter> filters = getQueryFilters(queryParams);

        return () -> Optional.of(filters);
    }

    private static void parseFilters(List<String> values, Matcher matcher, List<QueryFilter> filters) {
        String fieldStr = matcher.group(1);
        String operationStr = matcher.group(2);
        String nestedKey = matcher.group(3);     // Extract nested key if present

        QueryFilter.Field field = QueryFilter.Field.fromString(fieldStr);
        QueryFilter.Op operation = QueryFilter.Op.fromString(operationStr);

        Object value = nestedKey != null ? Map.of(nestedKey, values.getFirst()) : getFlatValue(values, field, operation);

        filters.add(QueryFilter.builder()
            .field(field)
            .operation(operation)
            .value(value)
            .build());
    }

    private static Object getFlatValue(List<String> values, QueryFilter.Field field, QueryFilter.Op operation) {
        return switch (field) {
            case SCOPE -> RequestUtils.toFlowScopes(values);
            default -> (operation == QueryFilter.Op.IN || operation == QueryFilter.Op.NOT_IN)
                ? List.of(URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8).replaceAll("[\\[\\]]", "").split(","))
                : values.size() == 1 ? values.getFirst() : values;
        };
    }
}