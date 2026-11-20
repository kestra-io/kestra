package io.kestra.core.utils;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugifyTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("test test ", "test-test"),
            Arguments.of("Test\t\t\ntest*", "test-test"),
            Arguments.of("--Test\t\t\ntest9*", "test-test9")
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void merge(String source, String transform) {
        assertThat(Slugify.of(source)).isEqualTo(transform);
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void shouldConvertStartCaseUnderTurkishLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Slugify.toStartCase("my-input")).isEqualTo("my Input");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
