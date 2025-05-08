package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class NanoIDFuntionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void checkStandardNanoId() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId() }}", Collections.emptyMap());
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21);
    }

    @Test
    void checkDifferentLength() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId(length) }}", Map.of("length", 8));
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(8);
    }

    @Test
    void checkDifferentAlphabet() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId(length,alphabet) }}", Map.of("length", 21, "alphabet", "abcdefghijklmnopqrstuvwxyz"));
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21);
        for (char c : rendered.toCharArray()) {
            assertThat(c).isGreaterThanOrEqualTo('a');
            assertThat(c).isLessThanOrEqualTo('z');
        }
    }

}


