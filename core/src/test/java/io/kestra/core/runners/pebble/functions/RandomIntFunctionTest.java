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
        () -> variableRenderer.render("{{randomInt(lower)}}", Map.of("lower", 10)));

    assertThrows(
        IllegalVariableEvaluationException.class,
        () -> variableRenderer.render("{{randomInt(upper)}}", Map.of("upper", 1)));
    assertThrows(
        IllegalVariableEvaluationException.class,
        () -> variableRenderer.render("{{randomInt()}}", Collections.emptyMap()));
  }

  @Test
  void testGenerateNumberPositive() throws IllegalVariableEvaluationException {
    String rendered =
        variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", 1, "upper", 10));
    assertThat(rendered, Integer.parseInt(rendered) >= 1 && Integer.parseInt(rendered) <= 10);
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
                "{{ randomInt(lower, upper) }}", Map.of("lower", 10, "upper", 1)));
  }

  @Test
  void testGenerateNumberNegative() throws IllegalVariableEvaluationException {
    String rendered =
        variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", -10, "upper", -1));
    assertThat(rendered, Integer.parseInt(rendered) >= -10 && Integer.parseInt(rendered) <= -1);
  }

    @Test
    void testGenerateNumberSame() throws IllegalVariableEvaluationException {
        String rendered =
            variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 10, "upper", 10));
        assertThat(rendered, Integer.parseInt(rendered) == 10);
    }
}
