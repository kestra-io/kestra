package io.kestra.core.serializers;

import io.kestra.core.models.flows.Flow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class YamlParserEscapeTest {

    @Test
    void shouldPreserveEscapesInYamlExpression() {
        String yaml = """
            id: test_escape
            namespace: dev

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "{{ ('a.b.c' | split('\\.') | first) }}"
            """;

        Flow parsed = YamlParser.parse(yaml, Flow.class);

        assertNotNull(parsed, "Flow should be parsed successfully");

        String flowAsString = parsed.toString();

        assertTrue(
            flowAsString.contains("split('\\\\.')"),
            "Backslash should be preserved in split expression"
        );
    }

    @Test
    void shouldHandleMultipleEscapedExpressions() {
        String yaml = """
            id: multi_escape
            namespace: dev

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "{{ ('a.b.c' | split('\\.') | last) }}"
            """;

        Flow parsed = YamlParser.parse(yaml, Flow.class);

        assertNotNull(parsed);

        String asText = parsed.toString();
        assertTrue(asText.contains("\\\\."), "Double backslash should remain after parsing");
    }

    @Test
    void shouldNotDoubleEscapeAlreadyCorrectExpression() {
        String yaml = """
            id: already_escaped
            namespace: dev

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "{{ ('a.b.c' | split('\\\\.') | first) }}"
            """;

        Flow parsed = YamlParser.parse(yaml, Flow.class);

        assertNotNull(parsed);
        String asText = parsed.toString();

        assertTrue(asText.contains("split('\\\\.')"), "Escape should not be multiplied");
    }

    @Test
    void shouldFailWithSingleEscape() {
        String yaml = """
            id: single_escape
            namespace: dev

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "{{ ('a.b.c' | split('\\.') | first) }}"
            """;

        Flow parsed = YamlParser.parse(yaml, Flow.class);
        assertNotNull(parsed);

        String expr = parsed.toString();
        assertTrue(expr.contains("\\\\."), "Single escape was auto-corrected to double");
    }
}
