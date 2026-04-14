package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.RegexTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class RegexFilterTest {

    @Inject
    VariableRenderer variableRenderer;

    // --- regexMatch ---

    @Test
    void regexMatchMatches() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'hello world' | regexMatch(regex='hello.*') }}", Map.of());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void regexMatchDoesNotMatch() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'hello world' | regexMatch(regex='^\\d+$') }}", Map.of());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void regexMatchPartialMatch() throws IllegalVariableEvaluationException {
        // find() is used, so partial matches return true
        String result = variableRenderer.render("{{ 'order-12345-done' | regexMatch(regex='\\d+') }}", Map.of());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void regexMatchNullInput() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ null | regexMatch(regex='.*') }}", Map.of());
        assertThat(result).isEqualTo("false");
    }

    // --- regexReplace ---

    @Test
    void regexReplaceSimple() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'hello world' | regexReplace(regex='world', replacement='java') }}", Map.of());
        assertThat(result).isEqualTo("hello java");
    }

    @Test
    void regexReplaceAllOccurrences() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'aa1bb2cc3' | regexReplace(regex='\\d', replacement='-') }}", Map.of());
        assertThat(result).isEqualTo("aa-bb-cc-");
    }

    @Test
    void regexReplaceWithCaptureGroup() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'aa1bb2cc3' | regexReplace(regex='(\\d)', replacement='[$1]') }}", Map.of());
        assertThat(result).isEqualTo("aa[1]bb[2]cc[3]");
    }

    @Test
    void regexReplaceNoMatch() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'hello' | regexReplace(regex='\\d+', replacement='X') }}", Map.of());
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void regexReplaceNullInput() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ null | regexReplace(regex='.*', replacement='x') }}", Map.of());
        assertThat(result).isEmpty();
    }

    // --- regexExtract ---

    @Test
    void regexExtractFullMatch() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'order-12345-done' | regexExtract(regex='\\d+') }}", Map.of());
        assertThat(result).isEqualTo("12345");
    }

    @Test
    void regexExtractCaptureGroup() throws IllegalVariableEvaluationException {
        // Use 3 capture groups and assert group 2 to verify correct group selection
        String result = variableRenderer.render("{{ 'order-111-222-333-done' | regexExtract(regex='order-(\\d+)-(\\d+)-(\\d+)', group=2) }}", Map.of());
        assertThat(result).isEqualTo("222");
    }

    @Test
    void regexExtractFirstMatchOnly() throws IllegalVariableEvaluationException {
        // Only the first match is returned
        String result = variableRenderer.render("{{ 'abc-123-def-456' | regexExtract(regex='\\d+') }}", Map.of());
        assertThat(result).isEqualTo("123");
    }

    @Test
    void regexExtractNoMatch() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ 'hello' | regexExtract(regex='\\d+') }}", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void regexExtractNullInput() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ null | regexExtract(regex='\\d+') }}", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void regexExtractGroupOutOfBounds() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'order-12345' | regexExtract(regex='order-(\\d+)', group=5) }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Group index 5 is out of bounds");
    }

    @Test
    void regexExtractNegativeGroup() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'order-12345' | regexExtract(regex='order-(\\d+)', group=-1) }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Group index -1 is out of bounds");
    }

    // --- missing required arguments ---

    @Test
    void regexMatchMissingRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexMatch() }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The argument 'regex' is required");
    }

    @Test
    void regexReplaceMissingRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexReplace(replacement='x') }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The argument 'regex' is required");
    }

    @Test
    void regexReplaceMissingReplacement() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexReplace(regex='\\d+') }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The argument 'replacement' is required");
    }

    @Test
    void regexExtractMissingRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexExtract() }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The argument 'regex' is required");
    }

    // --- invalid regex ---

    @Test
    void regexMatchInvalidRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexMatch(regex='[unclosed') }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Invalid regex");
    }

    @Test
    void regexReplaceInvalidRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexReplace(regex='[unclosed', replacement='x') }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Invalid regex");
    }

    @Test
    void regexExtractInvalidRegex() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'hello' | regexExtract(regex='[unclosed') }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Invalid regex");
    }

    // --- ReDoS protection ---

    @Test
    void regexMatchReDoSShouldTimeout() {
        try {
            RegexTestUtils.resetAndSetTimeout(Duration.ofMillis(200));
            String evilInput = "a".repeat(25) + "b";

            assertThatThrownBy(() -> variableRenderer.render(
                "{{ '" + evilInput + "' | regexMatch(regex='(.*a){25}') }}", Map.of()
            ))
                .isInstanceOf(IllegalVariableEvaluationException.class)
                .hasMessageContaining("timed out");
        } finally {
            RegexTestUtils.reset();
        }
    }

    @Test
    void regexReplaceReDoSShouldTimeout() {
        try {
            RegexTestUtils.resetAndSetTimeout(Duration.ofMillis(200));
            String evilInput = "a".repeat(25) + "b";

            assertThatThrownBy(() -> variableRenderer.render(
                "{{ '" + evilInput + "' | regexReplace(regex='(.*a){25}', replacement='x') }}", Map.of()
            ))
                .isInstanceOf(IllegalVariableEvaluationException.class)
                .hasMessageContaining("timed out");
        } finally {
            RegexTestUtils.reset();
        }
    }

    @Test
    void regexExtractReDoSShouldTimeout() {
        try {
            RegexTestUtils.resetAndSetTimeout(Duration.ofMillis(200));
            String evilInput = "a".repeat(25) + "b";

            assertThatThrownBy(() -> variableRenderer.render(
                "{{ '" + evilInput + "' | regexExtract(regex='(.*a){25}') }}", Map.of()
            ))
                .isInstanceOf(IllegalVariableEvaluationException.class)
                .hasMessageContaining("timed out");
        } finally {
            RegexTestUtils.reset();
        }
    }
}
