package io.kestra.webserver.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;

import io.micronaut.http.exceptions.HttpStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestUtilsTest {
    @ParameterizedTest
    @CsvSource(
        {
            "timestamp:2023-12-18T14:32:14Z,timestamp,2023-12-18T14:32:14Z",
            "url:https://your@company.com,url,https://your@company.com",
            "city:Düsseldorf,city,Düsseldorf",
            "key:foo bar,key,foo bar",
        }
    )
    void toMap(String input, String key, String value) {
        final Map<String, String> resultMap = RequestUtils.toMap(List.of(input));

        assertThat(resultMap).containsEntry(key, value);
    }

    @Test
    void toMapNullHandling() {
        assertThat(RequestUtils.toMap(null)).isEqualTo(Map.of());
    }

    @Test
    void toMapWithMissingSeparator() {
        assertThrows(
            HttpStatusException.class,
            () -> RequestUtils.toMap(List.of("foo"))
        );
    }

    @Test
    void toMapWithDuplicates() {
        assertThrows(
            HttpStatusException.class,
            () -> RequestUtils.toMap(List.of("key:value1", "key:value2"))
        );
    }

    @Test
    void toMapWithSpaceInsideKey() {
        assertThrows(
            HttpStatusException.class,
            () -> RequestUtils.toMap(List.of("composite key:value"))
        );
    }

    @Test
    void toMapWithEmptyPart() {
        assertThrows(
            HttpStatusException.class,
            () -> RequestUtils.toMap(List.of("key:"))
        );

        assertThrows(
            HttpStatusException.class,
            () -> RequestUtils.toMap(List.of(":value"))
        );
    }

    @Test
    void toMapTrimWorks() {
        final Map<String, String> resultMap = RequestUtils.toMap(List.of(" key : value "));
        assertThat(resultMap).containsEntry("key", "value");
    }

    @Test
    void testMapLegacyParamsToFilters() {
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        ZonedDateTime endDate = ZonedDateTime.parse("2024-01-02T10:00:00Z");
        List<State.Type> state = List.of(State.Type.RUNNING, State.Type.FAILED);

        List<QueryFilter> filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            null,
            "test-query",
            "test-namespace",
            "test-flow",
            "test-trigger",
            null,
            startDate,
            endDate,
            null,
            List.of("key:value"),
            null,
            ExecutionRepositoryInterface.ChildFilter.MAIN,
            state,
            "worker-1",
            "test_trigger_id"
        );

        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.QUERY && f.value().equals("test-query")));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.NAMESPACE && f.value().equals("test-namespace")));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.FLOW_ID && f.value().equals("test-flow")));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.TRIGGER_ID && f.value().equals("test-trigger")));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.START_DATE && f.value().equals(startDate.toString())));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.END_DATE && f.value().equals(endDate.toString())));
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.STATE && f.value().equals(state)));
        assertTrue(filters.stream().anyMatch(f -> f.field() == Field.TRIGGER_EXECUTION_ID && f.value().equals("test_trigger_id")));
    }

    @Test
    void testMapLegacyParamsToFilters_timerangeShouldBeTransformedToStartdateFilter() {
        Duration timeRange = Duration.ofHours(24);

        List<QueryFilter> filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            timeRange,
            null,
            null,
            null,
            null
        );

        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.START_DATE && f.value() != null));
        assertTrue(filters.stream().noneMatch(f -> f.field() == QueryFilter.Field.END_DATE));
        assertTrue(filters.stream().noneMatch(f -> f.field() == QueryFilter.Field.TIME_RANGE));
    }

    @Test
    void testMapLegacyParamsToFiltersHandlesNulls() {
        List<QueryFilter> filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertTrue(filters.isEmpty(), "Filters should be empty.");
    }

    @Test
    void testMapLegacyParamsToFiltersHandlesNulls_withDate() {
        List<QueryFilter> filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertTrue(filters.size() == 1, "Filters should only contain default startDate filter when all inputs are null.");
        assertTrue(filters.stream().anyMatch(f -> f.field() == QueryFilter.Field.START_DATE && f.value() != null));
    }

    @Test
    void testToFlowScopesValid() {
        List<FlowScope> result = RequestUtils.toFlowScopes("USER,SYSTEM");

        assertEquals(2, result.size());
        assertTrue(result.contains(FlowScope.USER));
        assertTrue(result.contains(FlowScope.SYSTEM));
    }

    @Test
    void testToFlowScopesInvalidValue() {
        Exception exception = assertThrows(
            IllegalArgumentException.class, () -> RequestUtils.toFlowScopes("INVALID_SCOPE")
        );

        assertTrue(exception.getMessage().contains("Invalid FlowScope value"));
    }

    @Test
    void isSecureWhenRequestItselfIsSecure() {
        assertThat(RequestUtils.isSecure(request(true, null, null))).isTrue();
    }

    @Test
    void isSecureWhenPlainHttpWithNoForwardedHeader() {
        assertThat(RequestUtils.isSecure(request(false, null, null))).isFalse();
    }

    @Test
    void isSecureWhenXForwardedProtoIsHttps() {
        assertThat(RequestUtils.isSecure(request(false, "https", null))).isTrue();
    }

    @Test
    void isSecureWhenXForwardedProtoIsHttp() {
        assertThat(RequestUtils.isSecure(request(false, "http", null))).isFalse();
    }

    @Test
    void isSecureWhenForwardedHeaderProtoIsHttps() {
        assertThat(RequestUtils.isSecure(request(false, null, "proto=https"))).isTrue();
    }

    private static HttpRequest<?> request(boolean secure, String xForwardedProto, String forwarded) {
        HttpRequest<?> request = mock(HttpRequest.class);
        when(request.isSecure()).thenReturn(secure);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.contains(HttpHeaders.FORWARDED)).thenReturn(forwarded != null);
        when(headers.getAll(HttpHeaders.FORWARDED)).thenReturn(forwarded != null ? List.of(forwarded) : List.of());
        when(headers.get("X-Forwarded-Proto")).thenReturn(xForwardedProto);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    @ParameterizedTest
    @CsvSource(
        {
            "/kestra,/kestra",
            "kestra,/kestra",
            "/kestra/,/kestra",
            "/a//b/,/a/b",
            "/,''",
        }
    )
    void shouldNormalizeContextPath(String contextPath, String expected) {
        assertThat(RequestUtils.normalizeContextPath(contextPath)).isEqualTo(expected);
    }

    @Test
    void shouldNormalizeMissingContextPathToEmpty() {
        assertThat(RequestUtils.normalizeContextPath(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
        {
            "/kestra,/kestra/api/v1/flows,/api/v1/flows",
            "/kestra,/kestra,/",
            "/kestra,/kestraX/api/v1,/kestraX/api/v1",
            "/a/b,/a/b/api/v1,/api/v1",
            "'',/api/v1/flows,/api/v1/flows",
        }
    )
    void shouldStripContextPathOnSegmentBoundaryOnly(String contextPath, String path, String expected) {
        assertThat(RequestUtils.stripContextPath(contextPath, path)).isEqualTo(expected);
    }
}
