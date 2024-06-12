package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
        assertThat(render, is(expected));
    }
}
