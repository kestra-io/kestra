package io.kestra.webserver.utils;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.flows.FlowScope;

import io.micronaut.http.exceptions.HttpStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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

}
