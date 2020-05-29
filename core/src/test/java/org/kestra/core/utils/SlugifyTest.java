package org.kestra.core.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        assertThat(Slugify.of(source), is(transform));
    }
}
