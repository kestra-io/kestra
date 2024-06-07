package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
@KestraTest
public class FlattenFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void flatten() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of(
            "nestedList", Arrays.asList(
                "You're doing great! Keep it up!",
                "Sending positive vibes your way!",
                Arrays.asList("You're awesome!", "Keep shining!"),
                "Believe in yourself!",
                Arrays.asList("You're making a difference!", "You've got this!"),
                "You're capable of amazing things!",
                "Stay positive and keep going!",
                "You're doing fantastic!"
            )
        );

        String render = variableRenderer.render("{{ nestedList | flatten | first }}", vars);
        String expected = "You're doing great! Keep it up!";

        assertThat(render, is(expected));
    }
}
