package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class UrlDecodeFilter {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void urldecode() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'Kestra rulez !' | urlencode | urldecode }}", Map.of());
        assertThat(render, is("Kestra rulez !"));
    }
}
