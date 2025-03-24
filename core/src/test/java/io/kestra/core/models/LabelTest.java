package io.kestra.core.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class LabelTest {

    @Test
    void shouldGetNestedMapGivenDistinctLabels() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label(Label.USERNAME, "test"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        Assertions.assertEquals(
            Map.of("system", Map.of("username", "test", "correlationId", "id")),
            result
        );
    }

    @Test
    void shouldGetNestedMapGivenDuplicateLabels() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label(Label.USERNAME, "test1"),
            new Label(Label.USERNAME, "test2"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        Assertions.assertEquals(
            Map.of("system", Map.of("username", "test1", "correlationId", "id")),
            result
        );
    }
}