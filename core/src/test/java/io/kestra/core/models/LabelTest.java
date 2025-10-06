package io.kestra.core.models;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LabelTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldGetNestedMapGivenDistinctLabels() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label(Label.USERNAME, "test"),
            new Label(Label.CORRELATION_ID, "id"),
            new Label("", "bar"),
            new Label(null, "bar"),
            new Label("foo", ""),
            new Label("baz", null)
            )
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
    void toNestedMapShouldIgnoreEmptyOrNull() {
        Map<String, Object> result = Label.toNestedMap(List.of(
            new Label("", "bar"),
            new Label(null, "bar"),
            new Label("foo", ""),
            new Label("baz", null))
        );

        assertThat(result).isEmpty();
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
    void toMapShouldIgnoreEmptyOrNull() {
        Map<String, String> result = Label.toMap(List.of(
            new Label("", "bar"),
            new Label(null, "bar"),
            new Label("foo", ""),
            new Label("baz", null))
        );

        assertThat(result).isEmpty();
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

    @Test
    void deduplicateShouldIgnoreEmptyAndNull() {
        List<Label> result = Label.deduplicate(List.of(
            new Label("", "bar"),
            new Label(null, "bar"),
            new Label("foo", ""),
            new Label("baz", null))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldValidateEmpty() {
        Optional<ConstraintViolationException> validLabelResult = modelValidator.isValid(new Label("foo", "bar"));
        assertThat(validLabelResult.isPresent()).isFalse();

        Optional<ConstraintViolationException> emptyValueLabelResult = modelValidator.isValid(new Label("foo", ""));
        assertThat(emptyValueLabelResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> emptyKeyLabelResult = modelValidator.isValid(new Label("", "bar"));
        assertThat(emptyKeyLabelResult.isPresent()).isTrue();
    }
}