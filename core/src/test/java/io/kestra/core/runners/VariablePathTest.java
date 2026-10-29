package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariablePathTest {

    @Test
    void shouldWriteBackTheKeyItWasParsedFromWhenSegmentsNeedBrackets() {
        String key = "outputs.loop[\"a \\\"b\\\"\"].rows[0].code";

        VariablePath path = VariablePath.parse(key);

        assertThat(path.segments()).containsExactly("outputs", "loop", "a \"b\"", "rows", 0, "code");
        assertThat(path.toString()).isEqualTo(key);
    }

    @Test
    void shouldSetValueWithoutChangingTheOriginalVariables() {
        Map<String, Object> variables = Map.of("outputs", Map.of("fetch", Map.of("rows", List.of(Map.of("code", "A"), Map.of("code", "B")))));

        Map<String, Object> overridden = VariablePath.parse("outputs.fetch.rows[1].code").set(variables, "C");

        assertThat(VariablePath.parse("outputs.fetch.rows[1].code").resolve(overridden)).contains("C");
        assertThat(VariablePath.parse("outputs.fetch.rows[0].code").resolve(overridden)).contains("A");
        assertThat(VariablePath.parse("outputs.fetch.rows[1].code").resolve(variables)).contains("B");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "[0]", "outputs.", "outputs..a", "outputs[x]", "outputs[\"a]", "outputs.a-b" })
    void shouldRejectKeyThatIsNotAPlainPath(String key) {
        assertThatThrownBy(() -> VariablePath.parse(key)).isInstanceOf(IllegalArgumentException.class);
    }
}
