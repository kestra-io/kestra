package io.kestra.core.runners.pebble.functions;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@MicronautTest
class KSUIDFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    private static final Pattern KSUID_PATTERN = Pattern.compile("^[0-9A-Za-z]{27}$");

    @Test
    void testKsuidGeneration() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render(
            "{{ ksuid() }}", Collections.emptyMap());


        assertThat(rendered).isNotEmpty();
        assertThat(KSUID_PATTERN.matcher(rendered).matches()).isTrue();
    }

    @Test
    void testKsuidUniqueness() throws IllegalVariableEvaluationException {
        String ksuid1 = variableRenderer.render(
            "{{ ksuid() }}", Collections.emptyMap());
        String ksuid2 = variableRenderer.render(
            "{{ ksuid() }}", Collections.emptyMap());

        assertThat(ksuid1).isNotEqualTo(ksuid2);
    }
}
