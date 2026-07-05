package io.kestra.core.runners.pebble.functions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
public class NanoIDFuntionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void checkStandardNanoId() throws Exception {
        String rendered = variableRenderer.render(
            "{{ nanoId() }}", Collections.emptyMap()
        );
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21L);
    }

    @Test
    void checkDifferentLength() throws Exception {
        String rendered = variableRenderer.render(
            "{{ nanoId(length) }}", Map.of("length", 8L)
        );
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(8L);
    }

    @Test
    void checkDifferentAlphabet() throws Exception {
        String rendered = variableRenderer.render(
            "{{ nanoId(length,alphabet) }}", Map.of("length", 21L, "alphabet", ":;<=>?@")
        );
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
        assertThat(rendered.length()).isEqualTo(21L);
        for (char c : rendered.toCharArray()) {
            assertThat(c).isGreaterThanOrEqualTo(':');
            assertThat(c).isLessThanOrEqualTo('@');
        }
    }

    @Test
    void shouldGenerateEveryCharacterWhenAlphabetIsNotAPowerOfTwo() throws Exception {
        // Given a 7-character (non power of two) alphabet
        // When drawing enough characters
        String rendered = variableRenderer.render(
            "{{ nanoId(length,alphabet) }}", Map.of("length", 1000L, "alphabet", "abcdefg")
        );

        // Then every character of the alphabet must be reachable (no '& mask' skew)
        Set<Character> seen = new HashSet<>();
        for (char c : rendered.toCharArray()) {
            seen.add(c);
        }
        assertThat(seen).containsExactlyInAnyOrder('a', 'b', 'c', 'd', 'e', 'f', 'g');
    }

    @Test
    void shouldThrowWhenAlphabetIsLongerThanTheByteRange() {
        // Given an alphabet longer than the 256-value byte range (non power of two)
        String alphabet = "a".repeat(257);

        // When rendering
        // Then it fails fast with a descriptive error instead of looping forever
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ nanoId(length,alphabet) }}", Map.of("length", 21L, "alphabet", alphabet)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("'alphabet' must not contain more than: 256");
    }

    @Test
    void shouldThrowWhenAlphabetIsEmpty() {
        // Given an empty alphabet
        // When rendering
        // Then it fails with a descriptive error instead of an ArrayIndexOutOfBoundsException
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ nanoId(alphabet='') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("'alphabet' must not be empty");
    }

    @Test
    void shouldThrowWhenLengthIsNegative() {
        // Given a negative length
        // When rendering
        // Then it fails with a descriptive error instead of a NegativeArraySizeException
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ nanoId(length=-1) }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("'length' must be greater than: 0");
    }

    @Test
    void shouldThrowWhenLengthIsZero() {
        // Given a zero length
        // When rendering
        // Then it fails with a descriptive error
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ nanoId(length=0) }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("'length' must be greater than: 0");
    }

}
