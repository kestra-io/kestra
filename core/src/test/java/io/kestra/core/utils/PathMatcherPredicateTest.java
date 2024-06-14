package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathMatcherPredicateTest {

    @Test
    void shouldSupportGlobExpression() {
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("glob:**/*"));
        assertEquals(List.of("glob:**/*"), predicate.syntaxAndPatterns());
    }

    @Test
    void shouldSupportRegexExpression() {
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("regex:.*\\.json"));
        assertEquals(List.of("regex:.*\\.json"), predicate.syntaxAndPatterns());
    }

    @Test
    void shouldAddMissingWildcardToGlobExpressions() {
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("test.txt"));
        assertEquals(List.of("glob:**/test.txt"), predicate.syntaxAndPatterns());
    }

    @Test
    void shouldUseGlobPatternForExpressionWithNoPrefix() {
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("**/*"));
        assertEquals(List.of("glob:**/*"), predicate.syntaxAndPatterns());
    }

    @Test
    void shouldAddBasePathForExpressionWithNoPrefix() {
        assertEquals(List.of("glob:/sub/dir/**/*"),
            PathMatcherPredicate.matches(Path.of("/sub/dir"), List.of("**/*")).syntaxAndPatterns()
        );

        assertEquals(List.of("glob:/sub/dir/**/*"),
            PathMatcherPredicate.matches(Path.of("/sub/dir"), List.of("/**/*")).syntaxAndPatterns()
        );
    }

    @Test
    void shouldMatchAllGivenRecursiveGlobExpressionAndNoBasePath() {
        // Given
        List<Path> paths = Stream.of("/base/test.txt", "/base/sub/dir/test.txt").map(Path::of).toList();
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("**/*.txt"));
        // When
        List<Path> filtered = paths.stream().filter(predicate).toList();
        // Then
        assertEquals(paths, filtered);
    }

    @Test
    void shouldMatchAllGivenSimpleExpressionAndNoBasePath() {
        // Given
        List<Path> paths = Stream.of("/base/test.txt", "/base/sub/dir/test.txt").map(Path::of).toList();
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(List.of("test.txt"));
        // When
        List<Path> filtered = paths.stream().filter(predicate).toList();
        // Then
        assertEquals(paths, filtered);
    }

    @Test
    void shouldMatchGivenSimpleExpressionAndBasePath() {
        // Given
        List<Path> paths = Stream.of("/base/test.txt", "/base/sub/dir/test.txt").map(Path::of).toList();
        PathMatcherPredicate predicate = PathMatcherPredicate.matches(Path.of("/base"), List.of("test.txt"));
        // When
        List<Path> filtered = paths.stream().filter(predicate).toList();
        // Then
        assertEquals(List.of(Path.of("/base/test.txt")), filtered);
    }

    @Test
    void shouldMatchGivenIncludeAndExcludeExpressions() {
        // Given
        List<Path> paths = List.of(
            // When
            Path.of("/a/b/c/1"),
            Path.of("/a/2"),
            Path.of("/b/c/d/3"),
            Path.of("/b/d/4"),
            Path.of("/c/5")
        );
        Predicate<Path> predicate = PathMatcherPredicate.builder()
            .includes(List.of("/a/**", "c/**"))
            .excludes(List.of("**/2"))
            .build();

        // When
        List<Path> filtered = paths.stream().filter(predicate).toList();

        // Then
        assertThat(filtered, containsInAnyOrder(
            is(Path.of("/a/b/c/1")),
            is(Path.of("/b/c/d/3")),
            is(Path.of("/c/5"))
        ));

    }
}