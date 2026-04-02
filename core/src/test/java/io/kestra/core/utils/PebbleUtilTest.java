package io.kestra.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PebbleUtilTest {

    @Test
    void openingBlockDelimitersContainsPrintAndExecute() {
        assertThat(PebbleUtil.openingBlockDelimiters()).containsExactly("{{", "{%");
    }

    @Test
    void closingBlockDelimitersContainsPrintAndExecute() {
        assertThat(PebbleUtil.closingBlockDelimiters()).containsExactly("}}", "%}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{{ secret('KEY') }}",
        "{% if true %}val{% endif %}",
        "prefix {{ expr }} suffix"
    })
    void containsOpeningBlockDelimiterReturnsTrue(String value) {
        assertThat(PebbleUtil.containsOpeningBlockDelimiter(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-text", "{# comment #}", "no delimiters here"})
    void containsOpeningBlockDelimiterReturnsFalse(String value) {
        assertThat(PebbleUtil.containsOpeningBlockDelimiter(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{ expr }}", "{%- raw -%}"})
    void startsWithOpeningBlockDelimiterReturnsTrue(String value) {
        assertThat(PebbleUtil.startsWithOpeningBlockDelimiter(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-text", "prefix {{ expr }}", "{# comment"})
    void startsWithOpeningBlockDelimiterReturnsFalse(String value) {
        assertThat(PebbleUtil.startsWithOpeningBlockDelimiter(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{ expr }}", "{% if true %}val{% endif %}"})
    void endsWithClosingBlockDelimiterReturnsTrue(String value) {
        assertThat(PebbleUtil.endsWithClosingBlockDelimiter(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-text", "{{ expr }} suffix", "comment #}"})
    void endsWithClosingBlockDelimiterReturnsFalse(String value) {
        assertThat(PebbleUtil.endsWithClosingBlockDelimiter(value)).isFalse();
    }
}
