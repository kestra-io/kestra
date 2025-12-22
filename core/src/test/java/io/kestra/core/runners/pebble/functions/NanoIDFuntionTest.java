package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class NanoIDFuntionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void checkStandardNanoId() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId() }}", Collections.emptyMap());
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21L);
    }

    @Test
    void checkDifferentLength() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId(length) }}", Map.of("length", 8L));
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(8L);
    }

    @Test
    void checkDifferentAlphabet() throws Exception {
        String rendered =
            variableRenderer.render(
                "{{ nanoId(length,alphabet) }}", Map.of("length", 21L, "alphabet", ":;<=>?@"));
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21L);
        for (char c : rendered.toCharArray()) {
            assertThat(c).isGreaterThanOrEqualTo(':');
            assertThat(c).isLessThanOrEqualTo('@');
        }
    }

}


