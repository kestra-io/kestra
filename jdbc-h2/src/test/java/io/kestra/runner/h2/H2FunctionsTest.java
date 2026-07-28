package io.kestra.runner.h2;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class H2FunctionsTest {
    @Test
    public void jqNull() {
        String jqString = H2Functions.jqString("{\"a\": null}", ".a");
        assertThat(jqString).isNull();
    }

    @Test
    public void jqString() {
        String jqString = H2Functions.jqString("{\"a\": \"b\"}", ".a");
        assertThat(jqString).isEqualTo("b");

        // on arrays, it will use the first element
        jqString = H2Functions.jqString("{\"labels\":[{\"key\":\"a\",\"value\":\"aValue\"},{\"key\":\"b\",\"value\":\"bValue\"}]}", ".labels[].value");
        assertThat(jqString).isEqualTo("aValue");
    }

    @Test
    public void jqStringWithArray() {
        String jqString = H2Functions.jqString("""
            {"a": [{"b": "c", "d": "e"}]}
            """, ".a[].b");
        assertThat(jqString).isEqualTo("c");
    }

    @Test
    public void jqBoolean() {
        Boolean jqString = H2Functions.jqBoolean("{\"a\": true}", ".a");
        assertThat(jqString).isTrue();
    }

    @Test
    public void jqInteger() {
        Integer jqString = H2Functions.jqInteger("{\"a\": 2147483647}", ".a");
        assertThat(jqString).isEqualTo(2147483647);
    }

    @Test
    public void jqLong() {
        Long jqString = H2Functions.jqLong("{\"a\": 9223372036854775807}", ".a");
        assertThat(jqString).isEqualTo(9223372036854775807L);
    }

    @Test
    public void jqStringArray() {
        String[] jqString = H2Functions.jqStringArray("{\"a\": [\"1\", \"2\", \"3\"]}", ".a");
        assertThat(List.of(jqString)).containsExactlyInAnyOrder("1", "2", "3");
    }

    @Test
    void shouldEscapeJqStringNullValue() {
        // Given / When / Then
        assertThat(H2Functions.escapeJqString(null)).isNull();
    }

    @Test
    void shouldEscapeJqStringMetacharacters() {
        // Given
        String input = "key\\with\"quotes";

        // When
        String escaped = H2Functions.escapeJqString(input);

        // Then — backslash must be escaped before double-quote to avoid double-escaping
        assertThat(escaped).isEqualTo("key\\\\with\\\"quotes");
    }

    @Test
    void shouldEscapeJqStringNamedControlCharacters() {
        // Given — named control characters: LF, CR, TAB, BS, FF
        assertThat(H2Functions.escapeJqString("\n")).isEqualTo("\\n");
        assertThat(H2Functions.escapeJqString("\r")).isEqualTo("\\r");
        assertThat(H2Functions.escapeJqString("\t")).isEqualTo("\\t");
        assertThat(H2Functions.escapeJqString("\b")).isEqualTo("\\b");
        assertThat(H2Functions.escapeJqString("\f")).isEqualTo("\\f");
    }

    @Test
    void shouldEscapeJqStringRawControlCharacters() {
        // Given — U+0001 (SOH) and U+001F (US), which have no named JSON escape
        assertThat(H2Functions.escapeJqString("")).isEqualTo("\\u0001");
        assertThat(H2Functions.escapeJqString("")).isEqualTo("\\u001f");
    }

    @Test
    void shouldEscapeJqStringAndProduceValidJqProgram() {
        // Given — a key crafted to break out of a jq string literal if unescaped
        String maliciousKey = "x\") | .password | (\"";
        String escaped = H2Functions.escapeJqString(maliciousKey);

        // When — embed in a real jq expression and evaluate
        String json = "{\"labels\":[{\"key\":\"safe\",\"value\":\"v\"}]}";
        String expression = ".labels[]? | select(.key == \"" + escaped + "\") | .value";
        String result = H2Functions.jqString(json, expression);

        // Then — no match (injection neutralised), no exception
        assertThat(result).isNull();
    }
}
