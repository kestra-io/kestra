package io.kestra.core.utils;

import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternsTest {

    @Test
    void shouldReturnSameCompiledInstanceWhenSameRegex() {
        assertThat(Patterns.of("io\\.kestra\\..*")).isSameAs(Patterns.of("io\\.kestra\\..*"));
        assertThat(Patterns.of("io\\.kestra\\..*").matcher("io.kestra.plugin.core.log.Log").matches()).isTrue();
    }

    @Test
    void shouldThrowWhenRegexIsInvalid() {
        assertThatThrownBy(() -> Patterns.of("[unclosed")).isInstanceOf(PatternSyntaxException.class);
    }
}
