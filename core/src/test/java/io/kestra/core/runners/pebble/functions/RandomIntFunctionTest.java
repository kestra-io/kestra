package io.kestra.core.runners.pebble.functions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KestraTest
class RandomIntFunctionTest {
  @Inject VariableRenderer variableRenderer;

  @Test
  void missingParameter() {
    assertThrows(
        IllegalVariableEvaluationException.class,
        () -> variableRenderer.render("{{randomInt(lower)}}", Map.of("lower", 10L)));

    assertThrows(
        IllegalVariableEvaluationException.class,
        () -> variableRenderer.render("{{randomInt(upper)}}", Map.of("upper", 1L)));
    assertThrows(
        IllegalVariableEvaluationException.class,
        () -> variableRenderer.render("{{randomInt()}}", Collections.emptyMap()));
  }

  @Test
  void testGenerateNumberPositive() throws IllegalVariableEvaluationException {
    String rendered =
        variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", 1L, "upper", 10L));
    assertThat(rendered, Long.parseLong(rendered) >= 1L && Long.parseLong(rendered) <= 10L);
  }

    @Test
    void testGenerateNumberPositiveString() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ randomInt(lower, upper) }}", Map.of("lower", "1", "upper", "10")));
    }

    @Test
  void testGenerateNumberUpperLessThanLower() {
    assertThrows(
        IllegalVariableEvaluationException.class,
        () ->
            variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 10L, "upper", 1L)));
  }

  @Test
  void testGenerateNumberNegative() throws IllegalVariableEvaluationException {
    String rendered =
        variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", -10L, "upper", -1L));
    assertThat(rendered, Long.parseLong(rendered) >= -10L && Long.parseLong(rendered) <= -1L);
  }

    @Test
    void testGenerateNumberSame() throws IllegalVariableEvaluationException {
        String rendered =
            variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 10L, "upper", 10L));
        assertThat(rendered, Long.parseLong(rendered) == 10);
    }
}
