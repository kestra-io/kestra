package io.kestra.core.utils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexUtilsTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);

    @Test
    void shouldMatchSimplePattern() {
        assertThat(RegexUtils.matches("\\d+", "12345")).isTrue();
        assertThat(RegexUtils.matches("\\d+", "abc")).isFalse();
    }

    @Test
    void shouldMatchCompiledPattern() {
        Pattern pattern = Pattern.compile("[a-z]+");
        assertThat(RegexUtils.matches(pattern, "hello")).isTrue();
        assertThat(RegexUtils.matches(pattern, "123")).isFalse();
    }

    @Test
    void shouldReplaceAll() {
        String result = RegexUtils.replaceAll("aa1bb2cc3", "(\\d)", "-$1-");
        assertThat(result).isEqualTo("aa-1-bb-2-cc-3-");
    }

    @Test
    void shouldCreateWorkingMatcher() {
        Pattern pattern = Pattern.compile("(\\w+)@(\\w+)");
        Matcher matcher = RegexUtils.matcher(pattern, "user@host");
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("user");
        assertThat(matcher.group(2)).isEqualTo("host");
    }

    @Test
    void shouldTimeoutOnCatastrophicBacktracking() {
        // Pattern that causes exponential backtracking even in Java 25
        String evilPattern = "(.*a){25}";
        String evilInput = "a".repeat(25) + "b";

        assertThatThrownBy(() -> RegexUtils.matches(evilPattern, evilInput, SHORT_TIMEOUT))
            .isInstanceOf(RegexUtils.RegexTimeoutException.class)
            .hasMessageContaining("timed out");
    }

    @Test
    void shouldTimeoutOnReplaceAllWithBacktracking() {
        String evilPattern = "(.*a){25}";
        String evilInput = "a".repeat(25) + "b";

        assertThatThrownBy(() -> RegexUtils.replaceAll(evilInput, evilPattern, "x", SHORT_TIMEOUT))
            .isInstanceOf(RegexUtils.RegexTimeoutException.class);
    }

    @Test
    void shouldNotTimeoutOnSafePatterns() {
        // Safe patterns should complete quickly even with a short timeout
        assertThat(RegexUtils.matches("^[a-z]+$", "a".repeat(10000), SHORT_TIMEOUT)).isTrue();
        assertThat(RegexUtils.replaceAll("a".repeat(10000), "a", "b", SHORT_TIMEOUT)).isEqualTo("b".repeat(10000));
    }

    @Test
    void shouldRespectConfiguredTimeout() {
        try {
            RegexUtils.resetInit();
            RegexUtils.setTimeout(Duration.ofSeconds(5));
            assertThat(RegexUtils.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        } finally {
            RegexUtils.resetInit();
        }
    }

    @Test
    void shouldIgnoreSecondSetTimeout() {
        try {
            RegexUtils.resetInit();
            RegexUtils.setTimeout(Duration.ofSeconds(7));
            RegexUtils.setTimeout(Duration.ofSeconds(99));
            assertThat(RegexUtils.getTimeout()).isEqualTo(Duration.ofSeconds(7));
        } finally {
            RegexUtils.resetInit();
        }
    }

    static Stream<Arguments> unsafeUserRegex() {
        return Stream.of(
            // Nested unbounded quantifiers
            Arguments.of("(a+)+b"),
            Arguments.of("(a*)*"),
            Arguments.of("(.*)*"),
            Arguments.of("(a+)*"),
            Arguments.of("(a{1,})+"),
            // Ambiguous alternation under repetition
            Arguments.of("(a|a)+$"),
            // Bounded-but-large nested quantifier ("Zalgo" shape) — no unbounded quantifier character at all
            Arguments.of("(a?){25}b"),
            Arguments.of("(a{1,3}){10}"),
            // Exempted groups multiply when stacked, and large inner bounds are not "small"
            Arguments.of("(a?){5}(a?){5}(a?){5}(a?){5}b"),
            Arguments.of("(a|a){3}b"),
            Arguments.of("(a{1,1000}){5}b"),
            // A separator that the repeated part can also match does not delimit the iterations
            Arguments.of("(A[a-z]+)*"),
            Arguments.of("(-[a-z\\-]+)*"),
            Arguments.of("(-.*)*"),
            Arguments.of("(\\w+\\s?)+"),
            Arguments.of("a".repeat(RegexUtils.MAX_USER_REGEX_LENGTH + 1))
        );
    }

    @ParameterizedTest
    @MethodSource("unsafeUserRegex")
    void shouldRejectUnsafeUserRegex(String pattern) {
        // Given a pattern prone to catastrophic backtracking (or too long to evaluate)
        // When checking whether it is safe to run against a database engine
        // Then it must be rejected
        assertThat(RegexUtils.isSafeUserRegex(pattern)).isFalse();
    }

    static Stream<Arguments> safeUserRegex() {
        return Stream.of(
            Arguments.of("[a-z]+"),
            Arguments.of("hello.*world"),
            Arguments.of("(abc)+"),
            Arguments.of("flow_\\d+"),
            Arguments.of("io\\.kestra\\..*"),
            // A single quantifier on an escaped literal '+' — no ambiguity, must not be mistaken for
            // a nested unbounded quantifier
            Arguments.of("(a\\+)+"),
            // Repeated at most once, or a small bounded count around bounded quantifiers
            Arguments.of("^(?:[A-Z]{2})?\\d+$"),
            Arguments.of("^(\\d{1,3}\\.){3}\\d{1,3}$"),
            Arguments.of("(jpg|png)?"),
            // Each iteration is delimited by a literal that the quantified part cannot match
            Arguments.of("^([a-z0-9]+\\.)*[a-z0-9]+$"),
            Arguments.of("^[a-z]+(-[a-z]+)*$"),
            Arguments.of("")
        );
    }

    @ParameterizedTest
    @MethodSource("safeUserRegex")
    void shouldAcceptSafeUserRegex(String pattern) {
        // Given a pattern with no catastrophic-backtracking risk
        // When checking whether it is safe to run against a database engine
        // Then it must be accepted
        assertThat(RegexUtils.isSafeUserRegex(pattern)).isTrue();
    }

    @Test
    void shouldRejectNullUserRegex() {
        // Given a null pattern
        // When checking whether it is safe to run against a database engine
        // Then it must be rejected
        assertThat(RegexUtils.isSafeUserRegex(null)).isFalse();
    }

    @Test
    void shouldDefaultConfigurationTimeoutToTenSeconds() {
        assertThat(new RegexConfiguration().getTimeout()).isEqualTo(Duration.ofSeconds(10));
    }
}
