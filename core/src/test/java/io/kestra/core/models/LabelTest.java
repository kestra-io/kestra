package io.kestra.core.models;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LabelTest {

    @Test
    void shouldGetNestedMapGivenDistinctLabels() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label(Label.USERNAME, "test"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        assertThat(result).isEqualTo(
            Map.of("system", Map.of("username", "test", "correlationId", "id"))
        );
    }

    @Test
    void shouldGetNestedMapGivenDuplicateLabels() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label(Label.USERNAME, "test1"),
            new Label(Label.USERNAME, "test2"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        assertThat(result).isEqualTo(
            Map.of("system", Map.of("username", "test2", "correlationId", "id"))
        );
    }

    @Test
    void shouldGetMapGivenDistinctLabels() {
        Map<String, String> result = Label.toMap(List.of(
            new Label(Label.USERNAME, "test"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        assertThat(result).isEqualTo(
            Map.of(Label.USERNAME, "test", Label.CORRELATION_ID, "id")
        );
    }

    @Test
    void shouldGetMapGivenDuplicateLabels() {
        Map<String, String> result = Label.toMap(List.of(
            new Label(Label.USERNAME, "test1"),
            new Label(Label.USERNAME, "test2"),
            new Label(Label.CORRELATION_ID, "id"))
        );

        assertThat(result).isEqualTo(
            Map.of(Label.USERNAME, "test2", Label.CORRELATION_ID, "id")
        );
    }

    @Test
    void shouldDuplicateLabelsWithKeyOrderKept() {
        List<Label> result = Label.deduplicate(List.of(
            new Label(Label.USERNAME, "test1"),
            new Label(Label.USERNAME, "test2"),
            new Label(Label.CORRELATION_ID, "id"),
            new Label(Label.USERNAME, "test3"))
        );

        assertThat(result).containsExactly(
            new Label(Label.USERNAME, "test3"),
            new Label(Label.CORRELATION_ID, "id")
        );
    }
}