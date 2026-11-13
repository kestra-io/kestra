package io.kestra.core.runners.pebble.filters;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class NumberFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("{{ \"12.3\" | number | className }}", Float.class.getName()),
            Arguments.of("{{ \"2147483647\" | number | className }}", Integer.class.getName()),
            Arguments.of("{{ \"9223372036854775807\" | number | className }}", Long.class.getName()),
            Arguments.of("{{ \"9223372036854775807\" | number('BIGDECIMAL') | className }}", BigDecimal.class.getName()),
            Arguments.of("{{ \"9223372036854775807\" | number('BIGINTEGER') | className }}", BigInteger.class.getName()),
            Arguments.of("{{ \"9223372036854775807\" | number('DOUBLE') | className }}", Double.class.getName())
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void run(String exp, String expected) throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(exp, Map.of());
        assertThat(render).isEqualTo(expected);
    }

    static Stream<Arguments> zeroPadded() {
        return Stream.of(
            Arguments.of("{{ \"010\" | number }}", "10"),
            Arguments.of("{{ \"08\" | number }}", "8"),
            Arguments.of("{{ \"08\" | number('INT') }}", "8"),
            Arguments.of("{{ \"-08\" | number('LONG') }}", "-8"),
            Arguments.of("{{ \"010\" | number('BIGINTEGER') }}", "10"),
            Arguments.of("{{ \"0\" | number }}", "0"),
            Arguments.of("{{ \"0.5\" | number }}", "0.5")
        );
    }

    @ParameterizedTest
    @MethodSource("zeroPadded")
    void shouldParseZeroPaddedStringsAsDecimal(String exp, String expected) throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render(exp, Map.of())).isEqualTo(expected);
    }

    @Test
    void shouldThrowNamingTheFilterWhenValueIsNotANumber() {
        assertThatThrownBy(() -> variableRenderer.render("{{ \"abc\" | number }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The 'number' filter cannot parse 'abc' as a number.");
    }
}
