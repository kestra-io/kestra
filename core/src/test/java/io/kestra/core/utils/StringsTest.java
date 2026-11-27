package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringsTest {

    @Test
    void shouldReturnNullWhenValueIsBlank() {
        assertThat(Strings.nullIfBlank(null)).isNull();
        assertThat(Strings.nullIfBlank("")).isNull();
        assertThat(Strings.nullIfBlank(" \t\n")).isNull();
    }

    @Test
    void shouldKeepValueUntrimmedWhenValueIsNotBlank() {
        assertThat(Strings.nullIfBlank(" value ")).isEqualTo(" value ");
    }

    @Test
    void shouldAppendEllipsisWhenValueIsLongerThanMaxLength() {
        assertThat(Strings.truncate("abcdef", 3)).isEqualTo("abc…");
    }

    @Test
    void shouldKeepValueWhenValueIsNotLongerThanMaxLength() {
        assertThat(Strings.truncate("abc", 3)).isEqualTo("abc");
        assertThat(Strings.truncate(null, 3)).isNull();
    }
}
