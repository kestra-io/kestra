package io.kestra.webserver.converters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.utils.RequestUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;

import static org.junit.jupiter.api.Assertions.*;

class QueryFilterFormatBinderTest {

    @Test
    void testGetQueryFiltersWithSimpleFilters() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[namespace][EQUALS]", List.of("test-namespace"),
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]", List.of("2024-01-01T00:00:00Z"),
            "filters[state][IN]", List.of("[RUNNING,FAILED]")
        );

        //WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN
        assertEquals(3, filters.size());

        assertTrue(
            filters.stream().anyMatch(
                f -> f.field() == QueryFilter.Field.NAMESPACE && f.operation() == QueryFilter.Op.EQUALS && f.value().equals("test-namespace")
            )
        );

        assertTrue(
            filters.stream().anyMatch(
                f -> f.field() == QueryFilter.Field.START_DATE && f.operation() == QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO && f.value().equals("2024-01-01T00:00:00Z")
            )
        );

        assertTrue(
            filters.stream().anyMatch(
                f -> f.field() == QueryFilter.Field.STATE && f.operation() == QueryFilter.Op.IN && f.value().equals(List.of("RUNNING", "FAILED"))
            )
        );
    }

    @Test
    void testGetQueryFiltersWithNestedFilters() {
        // GIVEN
        Map<String, List<String>> queryParams = Map.of(
            "filters[labels][EQUALS][key with special chars [(_-|&/*^)]]", List.of("value with special chars [(_-|&/*^)]")
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
            "filters[scope][EQUALS]", List.of("USER,SYSTEM")
        );
        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);
        // THEN
        assertEquals(1, filters.size());
        assertEquals(QueryFilter.Field.SCOPE, filters.getFirst().field());
        assertEquals(RequestUtils.toFlowScopes("USER,SYSTEM"), filters.getFirst().value());
    }

    @Test
    void testBindHttpRequest() {
        // GIVEN — a real HttpRequest with bracket-encoded filter params
        HttpRequest<?> request = HttpRequest.GET(
            UriBuilder.of("/")
                .queryParam("filters[namespace][EQUALS]", "test-namespace")
                .queryParam("filters[state][IN]", "[RUNNING,FAILED]")
                .build()
        );

        // WHEN — drive the parser the same way the bind() method does: read query params, then parse
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(request.getParameters().asMap());

        // THEN
        assertEquals(2, filters.size());

        assertTrue(
            filters.stream().anyMatch(
                f -> f.field() == QueryFilter.Field.NAMESPACE && f.operation() == QueryFilter.Op.EQUALS && f.value().equals("test-namespace")
            )
        );

        assertTrue(
            filters.stream().anyMatch(
                f -> f.field() == QueryFilter.Field.STATE && f.operation() == QueryFilter.Op.IN && f.value().equals(List.of("RUNNING", "FAILED"))
            )
        );
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

    @Test
    void shouldParseSingleOrGroupWithTwoBranches() {
        // GIVEN — two branches of one OR group, different values on the same field
        Map<String, List<String>> queryParams = Map.of(
            "filters[or][0][state][EQUALS]", List.of("RUNNING"),
            "filters[or][1][state][EQUALS]", List.of("FAILED")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — one OR node with two leaf children
        // Equivalent SQL: WHERE (state = 'RUNNING' OR state = 'FAILED')
        assertEquals(1, filters.size());
        QueryFilter or = filters.getFirst();
        assertTrue(or.isNode());
        assertEquals(QueryFilter.Logical.OR, or.logical());
        assertEquals(2, or.children().size());
        assertTrue(or.children().stream().allMatch(QueryFilter::isLeaf));
        Set<String> values = or.children().stream().map(c -> c.value().toString()).collect(Collectors.toSet());
        assertEquals(Set.of("RUNNING", "FAILED"), values);
    }

    @Test
    void shouldMergeFiltersWithSameOrIndexAsAndWrapper() {
        // GIVEN — two filters share the same OR branch index, so they implicitly AND inside the branch
        Map<String, List<String>> queryParams = Map.of(
            "filters[or][0][state][EQUALS]", List.of("RUNNING"),
            "filters[or][0][namespace][EQUALS]", List.of("io.kestra")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — single-branch OR wrapping an AND of the two leaves
        // Equivalent SQL: WHERE ((state = 'RUNNING' AND namespace = 'io.kestra'))
        assertEquals(1, filters.size());
        QueryFilter or = filters.getFirst();
        assertEquals(QueryFilter.Logical.OR, or.logical());
        assertEquals(1, or.children().size());
        QueryFilter wrapped = or.children().getFirst();
        assertTrue(wrapped.isNode());
        assertEquals(QueryFilter.Logical.AND, wrapped.logical());
        assertEquals(2, wrapped.children().size());
    }

    @Test
    void shouldCombineGlobalAndWithOrGroup() {
        // GIVEN — a top-level (implicit AND) leaf plus an OR group as siblings
        Map<String, List<String>> queryParams = Map.of(
            "filters[namespace][EQUALS]", List.of("io.kestra"),
            "filters[or][0][state][EQUALS]", List.of("RUNNING"),
            "filters[or][1][state][EQUALS]", List.of("FAILED")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — flat list: one leaf + one OR node; the dispatcher ANDs them at the root
        // Equivalent SQL: WHERE namespace = 'io.kestra' AND (state = 'RUNNING' OR state = 'FAILED')
        assertEquals(2, filters.size());
        long leafCount = filters.stream().filter(QueryFilter::isLeaf).count();
        long nodeCount = filters.stream().filter(QueryFilter::isNode).count();
        assertEquals(1, leafCount);
        assertEquals(1, nodeCount);
    }

    @Test
    void shouldParseDeeplyNestedOrInsideOr() {
        // GIVEN — nested OR inside OR alongside a global-AND leaf
        Map<String, List<String>> queryParams = Map.of(
            "filters[or][0][or][0][state][EQUALS]", List.of("A"),
            "filters[or][0][or][1][state][EQUALS]", List.of("B"),
            "filters[or][1][state][EQUALS]", List.of("C"),
            "filters[namespace][EQUALS]", List.of("ns")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — namespace leaf + outer OR whose first branch is itself an OR
        // Equivalent SQL: WHERE namespace = 'ns' AND ((state = 'A' OR state = 'B') OR state = 'C')
        assertEquals(2, filters.size());
        QueryFilter namespace = filters.stream().filter(QueryFilter::isLeaf).findFirst().orElseThrow();
        assertEquals("ns", namespace.value());

        QueryFilter outerOr = filters.stream().filter(QueryFilter::isNode).findFirst().orElseThrow();
        assertEquals(QueryFilter.Logical.OR, outerOr.logical());
        assertEquals(2, outerOr.children().size());

        QueryFilter innerOr = outerOr.children().stream().filter(QueryFilter::isNode).findFirst().orElseThrow();
        assertEquals(QueryFilter.Logical.OR, innerOr.logical());
        assertEquals(2, innerOr.children().size());
    }

    @Test
    void shouldRejectExcessiveNestingDepth() {
        // GIVEN — a single URL param with 4 [or][0] pairs (depth cap configured to 3)
        StringBuilder key = new StringBuilder("filters");
        for (int i = 0; i < 4; i++) {
            key.append("[or][0]");
        }
        key.append("[state][EQUALS]");
        Map<String, List<String>> queryParams = Map.of(key.toString(), List.of("X"));

        // WHEN / THEN — parser throws on the 4th descent; no SQL is generated
        // Equivalent SQL: <none — request is rejected at the binder>
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            QueryFilterFormatBinder.getQueryFilters(queryParams, 3, Integer.MAX_VALUE)
        );
        assertTrue(ex.getMessage().contains("depth"),
            "Expected depth error, got: " + ex.getMessage());
    }

    @Test
    void shouldRejectExcessiveNodeWidth() {
        // GIVEN — a single OR group with 21 sibling branches (width cap configured to 20)
        Map<String, List<String>> queryParams = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            queryParams.put("filters[or][" + i + "][state][EQUALS]", List.of("RUNNING"));
        }

        // WHEN / THEN — width check throws; the OR node would have 21 children
        // Equivalent SQL: <none — request is rejected at the binder>
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            QueryFilterFormatBinder.getQueryFilters(queryParams, Integer.MAX_VALUE, 20)
        );
        assertTrue(ex.getMessage().contains("width"),
            "Expected width error, got: " + ex.getMessage());
    }

    @Test
    void shouldRejectExcessiveRootWidth() {
        // GIVEN — one URL param with 21 repeated values (each becomes its own leaf at root)
        List<String> twentyOneValues = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOneValues.add("v" + i);
        }
        Map<String, List<String>> queryParams = Map.of(
            "filters[namespace][EQUALS]", twentyOneValues
        );

        // WHEN / THEN — root produces 21 sibling leaves, exceeding the width cap
        // Equivalent SQL: <none — request is rejected at the binder>
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            QueryFilterFormatBinder.getQueryFilters(queryParams, Integer.MAX_VALUE, 20)
        );
        assertTrue(ex.getMessage().contains("width"),
            "Expected width error, got: " + ex.getMessage());
    }

    @Test
    void shouldMergeLabelsWithinNodeContext() {
        // GIVEN — labels with nested keys spread across two OR branches; within each branch,
        // shared field+operation labels should merge into a single map-valued leaf
        Map<String, List<String>> queryParams = Map.of(
            "filters[or][0][labels][EQUALS][env]", List.of("prod"),
            "filters[or][0][labels][EQUALS][team]", List.of("backend"),
            "filters[or][1][labels][EQUALS][env]", List.of("dev")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — single OR with two leaves; branch 0 has merged labels {env=prod, team=backend}
        // Equivalent SQL (labels handling is db-specific; pseudo form):
        //   WHERE (labels MATCH {env=prod, team=backend} OR labels MATCH {env=dev})
        assertEquals(1, filters.size());
        QueryFilter or = filters.getFirst();
        assertEquals(QueryFilter.Logical.OR, or.logical());
        assertEquals(2, or.children().size());
        assertTrue(or.children().stream().allMatch(QueryFilter::isLeaf));
        assertTrue(or.children().stream().allMatch(c -> c.field() == QueryFilter.Field.LABELS));
    }

    @Test
    void shouldRemainBackwardCompatibleWithFlatLegacyFormat() {
        // GIVEN — a real production URL using the legacy flat format (no [and]/[or] prefix)
        HttpRequest<?> request = HttpRequest.GET(
            UriBuilder.of("/")
                .queryParam("filters[timeRange][EQUALS]", "PT24H")
                .queryParam("filters[flowId][IN]", "after-execution-error,after-execution-finally,avoidinfiniteexecutionloop")
                .build()
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(request.getParameters().asMap());

        // THEN — two flat leaves, no node wrapping (identical to pre-OR-support behavior)
        // Equivalent SQL: WHERE timeRange = 'PT24H'
        //                   AND flowId IN ('after-execution-error', 'after-execution-finally', 'avoidinfiniteexecutionloop')
        assertEquals(2, filters.size());
        assertTrue(filters.stream().allMatch(QueryFilter::isLeaf), "All filters should be leaves (no node wrapping)");

        QueryFilter timeRange = filters.stream()
            .filter(f -> f.field() == QueryFilter.Field.TIME_RANGE).findFirst().orElseThrow();
        assertEquals(QueryFilter.Op.EQUALS, timeRange.operation());
        assertEquals("PT24H", timeRange.value());

        QueryFilter flowId = filters.stream()
            .filter(f -> f.field() == QueryFilter.Field.FLOW_ID).findFirst().orElseThrow();
        assertEquals(QueryFilter.Op.IN, flowId.operation());
        assertEquals(
            List.of("after-execution-error", "after-execution-finally", "avoidinfiniteexecutionloop"),
            flowId.value()
        );
    }

    @Test
    void shouldAcceptCaseInsensitiveLogicalKeywords() {
        // GIVEN — the [or] keyword in mixed casing (caller convenience)
        Map<String, List<String>> queryParams = Map.of(
            "filters[OR][0][state][EQUALS]", List.of("RUNNING"),
            "filters[Or][1][state][EQUALS]", List.of("FAILED")
        );

        // WHEN
        List<QueryFilter> filters = QueryFilterFormatBinder.getQueryFilters(queryParams);

        // THEN — case is normalized, both branches land in the same OR node
        // Equivalent SQL: WHERE (state = 'RUNNING' OR state = 'FAILED')
        assertEquals(1, filters.size());
        assertEquals(QueryFilter.Logical.OR, filters.getFirst().logical());
        assertEquals(2, filters.getFirst().children().size());
    }
}
