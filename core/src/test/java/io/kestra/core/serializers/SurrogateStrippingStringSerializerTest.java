package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SurrogateStrippingStringSerializerTest {

    // ── sanitize() unit tests (no Jackson involved) ───────────────────────────

    @Test
    void sanitize_null_returnsNull() {
        assertThat(SurrogateStrippingStringSerializer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_plainString_returnsSameObject() {
        // Fast path: no allocation should happen when there are no surrogates.
        String s = "normal text";
        assertThat(SurrogateStrippingStringSerializer.sanitize(s)).isSameAs(s);
    }

    @Test
    void sanitize_loneLowSurrogate_replaced() {
        // \uDC59 is the lone low surrogate from the first incident in issue #14806.
        String result = SurrogateStrippingStringSerializer.sanitize("\uDC59 test");
        assertThat(result).doesNotContain("\uDC59");
        assertThat(result).contains(String.valueOf(SurrogateStrippingStringSerializer.REPLACEMENT_CHAR));
    }

    @Test
    void sanitize_loneHighSurrogate_replaced() {
        // \uD800 is a lone high surrogate with nothing following it.
        String result = SurrogateStrippingStringSerializer.sanitize("test\uD800text");
        assertThat(result).doesNotContain("\uD800");
        assertThat(result).contains(String.valueOf(SurrogateStrippingStringSerializer.REPLACEMENT_CHAR));
    }

    @Test
    void sanitize_validSurrogatePair_preserved() {
        // U+1D400 MATHEMATICAL BOLD CAPITAL A = \uD835\uDC00 — must survive untouched.
        String input = "\uD835\uDC00 math symbol";
        String result = SurrogateStrippingStringSerializer.sanitize(input);
        assertThat(result).contains("\uD835\uDC00");
        assertThat(result).doesNotContain(String.valueOf(SurrogateStrippingStringSerializer.REPLACEMENT_CHAR));
    }

    @Test
    void sanitize_reversedSurrogatePair_bothReplaced() {
        // Low before high: both are lone by definition.
        String result = SurrogateStrippingStringSerializer.sanitize("\uDC00\uD800");
        assertThat(result).isEqualTo(
            "" + SurrogateStrippingStringSerializer.REPLACEMENT_CHAR
               + SurrogateStrippingStringSerializer.REPLACEMENT_CHAR
        );
    }

    @Test
    void sanitize_firstIncidentSequence() {
        // Exact sequence from issue #14806: lone low \uDC59 then valid pair \uD835\uDC5E.
        String result = SurrogateStrippingStringSerializer.sanitize("\uDC59\uD835\uDC5E2 = lower limit of the");
        assertThat(result).doesNotStartWith("\uDC59");
        assertThat(result).contains("\uD835\uDC5E"); // valid pair must survive
    }

    @Test
    void sanitize_secondIncidentSequence() {
        // Sequence from issue #14806 comments: trailing lone high surrogate.
        String result = SurrogateStrippingStringSerializer.sanitize("(1 + \uD835\uDC561\uD835\uDC61)(1 + \uD835");
        assertThat(result).doesNotEndWith("\uD835");
    }

    // ── Integration tests via JacksonMapper.ofJson() ──────────────────────────

    @Test
    void shouldStripLoneHighSurrogate() throws Exception {
        ObjectMapper mapper = JacksonMapper.ofJson();

        String json = mapper.writeValueAsString("test\uD800text");

        assertThat(json).doesNotContain("\uD800");
    }

    @Test
    void shouldStripInvalidSurrogateInsideObject() throws Exception {
        ObjectMapper mapper = JacksonMapper.ofJson();

        String json = mapper.writeValueAsString(Map.of("key", "value\uD800bad"));

        assertThat(json).doesNotContain("\uD800");
    }

    @Test
    void shouldKeepValidStringUntouched() throws Exception {
        ObjectMapper mapper = JacksonMapper.ofJson();

        String json = mapper.writeValueAsString("normal text");

        assertThat(json).contains("normal text");
    }

    @Test
    void shouldNotThrowOnMinimalReproducerFromIssue() {
        // The exact task message from the reproducer workflow in issue #14806.
        ObjectMapper mapper = JacksonMapper.ofJson();

        assertThatCode(() ->
            mapper.writeValueAsString("\uDC59\uD835\uDC5E2 = lower limit of t")
        ).doesNotThrowAnyException();
    }
}