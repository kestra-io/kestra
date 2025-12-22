package io.kestra.core.models;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
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

    @Test
    void shouldValidateValidLabelKeys() {
        // Valid keys: start with lowercase; may contain letters, numbers, hyphens, underscores, periods
        assertThat(modelValidator.isValid(new Label("foo", "bar")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("foo-bar", "value")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("foo_bar", "value")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("foo123", "value")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("foo-bar_baz123", "value")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("a", "value")).isPresent()).isFalse();
        assertThat(modelValidator.isValid(new Label("foo.bar", "value")).isPresent()).isFalse(); // dot is allowed
    }

    @Test
    void shouldRejectInvalidLabelKeys() {

        Optional<ConstraintViolationException> spaceResult = modelValidator.isValid(new Label("foo bar", "value"));
        assertThat(spaceResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> uppercaseResult = modelValidator.isValid(new Label("Foo", "value"));
        assertThat(uppercaseResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> emojiResult = modelValidator.isValid(new Label("💩", "value"));
        assertThat(emojiResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> atSignResult = modelValidator.isValid(new Label("foo@bar", "value"));
        assertThat(atSignResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> colonResult = modelValidator.isValid(new Label("foo:bar", "value"));
        assertThat(colonResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> hyphenStartResult = modelValidator.isValid(new Label("-foo", "value"));
        assertThat(hyphenStartResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> underscoreStartResult = modelValidator.isValid(new Label("_foo", "value"));
        assertThat(underscoreStartResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> zeroResult = modelValidator.isValid(new Label("0", "value"));
        assertThat(zeroResult.isPresent()).isTrue();

        Optional<ConstraintViolationException> digitStartResult = modelValidator.isValid(new Label("9test", "value"));
        assertThat(digitStartResult.isPresent()).isTrue();
    }
}