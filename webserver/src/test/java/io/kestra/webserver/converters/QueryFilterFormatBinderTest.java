package io.kestra.webserver.converters;

import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryFilterFormatBinderTest {

    @Test
    void testGetQueryFiltersWithSimpleFilters() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[namespace][$eq]", List.of("test-namespace"),
            "filters[startDate][$gte]", List.of("2024-01-01T00:00:00Z"),
            "filters[state][$in]", List.of("[RUNNING,FAILED]")
        );

        //WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN
        assertEquals(3, filters.size());

        assertTrue(filters.stream().anyMatch(f ->
            f.field() == QueryFilter.Field.NAMESPACE && f.operation() == QueryFilter.Op.EQUALS && f.value().equals("test-namespace")
        ));

        assertTrue(filters.stream().anyMatch(f ->
            f.field() == QueryFilter.Field.START_DATE && f.operation() == QueryFilter.Op.GREATER_THAN && f.value().equals("2024-01-01T00:00:00Z")
        ));

        assertTrue(filters.stream().anyMatch(f ->
            f.field() == QueryFilter.Field.STATE && f.operation() == QueryFilter.Op.IN && f.value().equals(List.of("RUNNING", "FAILED"))
        ));
    }

    @Test
    void testGetQueryFiltersWithNestedFilters() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[labels][$eq][key with special chars [(_-|&/*^)]]", List.of("value with special chars [(_-|&/*^)]")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN
        assertEquals(1, filters.size());

        QueryFilter filter = filters.getFirst();
        assertEquals(QueryFilter.Field.LABELS, filter.field());
        assertEquals(QueryFilter.Op.EQUALS, filter.operation());
        assertEquals(Map.of("key with special chars [(_-|&/*^)]", "value with special chars [(_-|&/*^)]"), filter.value());
    }

    @Test
    void testGetQueryFiltersWithScopeParsing() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[scope][$eq]", List.of("USER,SYSTEM")
        );
        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);
        // THEN
        assertEquals(1, filters.size());
        assertEquals(QueryFilter.Field.SCOPE, filters.getFirst().field());
        assertEquals(RequestUtils.toFlowScopes(List.of("USER,SYSTEM")), filters.getFirst().value());
    }

    @Test
    void testBindHttpRequest() {
        // GIVEN
        HttpRequest<?> request = HttpRequest.GET(UriBuilder.of("/")
            .queryParam("filters[namespace][$eq]", "test-namespace")
            .queryParam("filters[state][$in]", "[RUNNING,FAILED]")
            .build());

        // WHEN
        QueryFilterFormatBinder binder = new QueryFilterFormatBinder();
        List<QueryFilter> filters = binder.bind(null, request).get();

        // THEN
        assertEquals(2, filters.size());

        assertTrue(filters.stream().anyMatch(f ->
            f.field() == QueryFilter.Field.NAMESPACE && f.operation() == QueryFilter.Op.EQUALS && f.value().equals("test-namespace")
        ));

        assertTrue(filters.stream().anyMatch(f ->
            f.field() == QueryFilter.Field.STATE && f.operation() == QueryFilter.Op.IN && f.value().equals(List.of("RUNNING", "FAILED"))
        ));
    }

    @Test
    void testGetQueryFiltersWithInvalidFilterPattern() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[invalid]", List.of("test-value")
        );
        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);
        // THEN
        assertEquals(0, filters.size(), "Invalid filters should be ignored");
    }
}
