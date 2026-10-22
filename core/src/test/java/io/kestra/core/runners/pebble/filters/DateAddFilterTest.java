package io.kestra.core.runners.pebble.filters;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class DateAddFilterTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void toDateAdd() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of(
            "day", 1,
            "timezone", "Australia/Perth"
        );
        String render = variableRenderer.render(
            "{{ \"2013-09-08T16:19:00+02\" | dateAdd(day, \"DAYS\") | date(\"yyyy-MM-dd HH:mm:ss z\", timeZone=render(timezone)) }}",
            vars
        );

        assertThat(render).isEqualTo("2013-09-09 22:19:00 AWST");
    }

    @Test
    void should_fail_with_invalid_day() {
        Map<String, Object> vars = Map.of(
            "day", "invalid",
            "timezone", "Australia/Perth"
        );
        assertThrows(
            IllegalVariableEvaluationException.class, () -> variableRenderer.render(
                "{{ \"2013-09-08T16:19:00+02\" | dateAdd(day, \"DAYS\") | date(\"yyyy-MM-dd HH:mm:ss z\", timeZone=render(timezone)) }}",
                vars
            )
        );
    }

    @Test
    void should_return_null_for_null_input() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of(
            "day", 1,
            "timezone", "Australia/Perth"
        );
        String render = variableRenderer.render(
            "{{ null | dateAdd(day, \"DAYS\") | date(\"yyyy-MM-dd HH:mm:ss z\", timeZone=render(timezone)) }}",
            vars
        );
        assertThat(render).isEmpty();
    }

    @MethodSource("longInput")
    @ParameterizedTest
    void should_get_as_long(InputWrapper wrapper) {
        assertThat(DateAddFilter.getAsLong(wrapper.value, 0, null)).isEqualTo(1L);
    }

    static Stream<InputWrapper> longInput() {
        return Stream.of(
            new InputWrapper(1L),
            new InputWrapper(1),
            new InputWrapper(new AtomicInteger(1)),
            new InputWrapper("1")
        );
    }

    @MethodSource("invalidInput")
    @ParameterizedTest
    void should_get_not_get_as_long(InputWrapper wrapper) {
        assertThrows(PebbleException.class, () -> DateAddFilter.getAsLong(wrapper.value, 0, null));
    }

    static Stream<InputWrapper> invalidInput() {
        return Stream.of(
            new InputWrapper(null),
            new InputWrapper("invalidString")
        );
    }
    @ParameterizedTest
    @ValueSource(strings = {"DAYS", "days", "Days"})
    void should_accept_case_insensitive_units(String unit) throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ \"2024-01-01T00:00:00Z\" | dateAdd(1, \"" + unit + "\") | date(\"yyyy-MM-dd\") }}"
        );

        assertThat(render).isEqualTo("2024-01-02");
    }

    @Test
    void should_fail_with_null_unit() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render(
                "{{ \"2024-01-01T00:00:00Z\" | dateAdd(1, null) }}"
            )
        );
    }

    @Test
    void should_fail_with_invalid_unit() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render(
                "{{ \"2024-01-01T00:00:00Z\" | dateAdd(1, \"fortnights\") }}"
            )
        );
    }

    //Parametrized test doesn't like Object as method parameter
    record InputWrapper(Object value) {
    }
}
