package io.kestra.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ToonUtilsTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void testSimpleObject() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                }
              }
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("type: object"));
        assertThat(toon, containsString("properties:"));
        assertThat(toon, containsString("name:"));
        assertThat(toon, containsString("type: string"));
    }

    @Test
    void testNestedObject() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "age": {
                      "type": "integer"
                    }
                  }
                }
              }
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("user:"));
        assertThat(toon, containsString("type: object"));
        assertThat(toon, containsString("name:"));
        assertThat(toon, containsString("age:"));
    }

    @Test
    void testPrimitiveArray() throws Exception {
        String json = """
            {
              "required": ["id", "name", "type"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("required[3]: id,name,type"));
    }

    @Test
    void testEmptyArray() throws Exception {
        String json = """
            {
              "items": []
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("items[0]:"));
    }

    @Test
    void testUniformObjectArray() throws Exception {
        String json = """
            {
              "users": [
                {
                  "id": 1,
                  "name": "Alice"
                },
                {
                  "id": 2,
                  "name": "Bob"
                }
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should use tabular format
        assertThat(toon, containsString("users[2]{id,name}:"));
        assertThat(toon, containsString(" 1,Alice"));
        assertThat(toon, containsString(" 2,Bob"));
    }

    @Test
    void testMixedArray() throws Exception {
        String json = """
            {
              "items": [
                {
                  "type": "string"
                },
                {
                  "type": "integer",
                  "minimum": 0
                }
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should use list format due to non-uniform structure
        assertThat(toon, containsString("items[2]:"));
        assertThat(toon, containsString("- type: string"));
        assertThat(toon, containsString("- type: integer"));
        assertThat(toon, containsString("minimum: 0"));
    }

    @Test
    void testPrimitiveValues() throws Exception {
        String json = """
            {
              "string": "hello",
              "numberAsString": 42,
              "decimal": 3.14,
              "boolean": true,
              "nullValue": null
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("string: hello"));
        assertThat(toon, containsString("numberAsString: 42"));
        assertThat(toon, containsString("decimal: 3.14"));
        assertThat(toon, containsString("boolean: true"));
        assertThat(toon, containsString("nullValue: null"));
    }

    @Test
    void testStringEscaping() throws Exception {
        String json = """
            {
              "withColon": "key:value",
              "withQuotes": "say \\"hello\\"",
              "withNewline": "line1\\nline2",
              "withComma": "a,b,c"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // These should be quoted due to special characters
        assertThat(toon, containsString("withColon: \"key:value\""));
        assertThat(toon, containsString("withQuotes:"));
        assertThat(toon, containsString("withNewline:"));
        assertThat(toon, containsString("withComma: \"a,b,c\""));
    }

    @Test
    void testReservedKeywords() throws Exception {
        String json = """
            {
              "truthValue": "true",
              "falseValue": "false",
              "nullString": "null"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should be quoted to distinguish from boolean/null primitives
        assertThat(toon, containsString("truthValue: \"true\""));
        assertThat(toon, containsString("falseValue: \"false\""));
        assertThat(toon, containsString("nullString: \"null\""));
    }

    @Test
    void testJsonSchemaPattern() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Unique identifier"
                },
                "tasks": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": {
                        "type": "string"
                      }
                    }
                  }
                }
              },
              "required": ["id"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("type: object"));
        assertThat(toon, containsString("properties:"));
        assertThat(toon, containsString("id:"));
        assertThat(toon, containsString("tasks:"));
        assertThat(toon, containsString("items:"));
        assertThat(toon, containsString("required[1]: id"));
    }

    @Test
    void testSizeReduction() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Unique identifier"
                },
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "integer"
                },
                "tags": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                }
              },
              "required": ["id", "name"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);
        String jsonMinified = MAPPER.writeValueAsString(node);

        // TOON should be more compact than minified JSON
        assertThat(toon.length(), lessThan(jsonMinified.length()));
        
        // Calculate reduction percentage
        double reduction = (1.0 - (double) toon.length() / jsonMinified.length()) * 100;
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BEFORE (JSON Format):");
        System.out.println("=".repeat(80));
        System.out.println(jsonMinified);
        System.out.println("\nSize: " + jsonMinified.length() + " bytes");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AFTER (TOON Format):");
        System.out.println("=".repeat(80));
        System.out.println(toon);
        System.out.println("\nSize: " + toon.length() + " bytes");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPARISON RESULTS:");
        System.out.println("=".repeat(80));
        System.out.printf("JSON size: %d bytes%n", jsonMinified.length());
        System.out.printf("TOON size: %d bytes%n", toon.length());
        System.out.printf("Reduction: %.1f%%%n", reduction);
        System.out.printf("Saved: %d bytes%n", jsonMinified.length() - toon.length());
        System.out.println("\nBenefits:");
        System.out.println("✓ Reduced token usage for LLM");
        System.out.println("✓ Faster response times");
        System.out.println("✓ More readable indentation-based format");
        System.out.println("✓ Less redundant syntax");
        System.out.println("=".repeat(80) + "\n");
        
        // Should have at least some reduction
        assertThat(reduction, greaterThan(0.0));
    }

    @Test
    void testEmptyObject() throws Exception {
        String json = "{}";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Empty object should produce empty string
        assertThat(toon, is(""));
    }

    @Test
    void testRootPrimitive() throws Exception {
        String json = "\"hello\"";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, is("hello"));
    }

    @Test
    void testRootNumber() throws Exception {
        String json = "42";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, is("42"));
    }

    @Test
    void testRootArray() throws Exception {
        String json = "[1, 2, 3]";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("[3]: 1,2,3"));
    }

    @Test
    void testNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ToonUtils.jsonToToon(null);
        });
    }

    @Test
    void testKeyFormatting() throws Exception {
        String json = """
            {
              "simple": "value",
              "with-dash": "value",
              "with space": "value",
              "with.dot": "value",
              "$special": "value"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Simple keys and keys with dots should be unquoted
        assertThat(toon, containsString("simple: value"));
        assertThat(toon, containsString("with.dot: value"));

        // Keys with dashes and spaces should be quoted
        assertThat(toon, containsString("\"with-dash\": value"));
        assertThat(toon, containsString("\"with space\": value"));
        assertThat(toon, containsString("\"$special\": value"));
    }

    @Test
    void testNestedArrays() throws Exception {
        String json = """
            {
              "matrix": [
                [1, 2],
                [3, 4]
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("matrix[2]:"));
        assertThat(toon, containsString("- [2]: 1,2"));
        assertThat(toon, containsString("- [2]: 3,4"));
    }

    @Test
    void testNumberFormatting() throws Exception {
        String json = """
            {
              "zero": 0,
              "negative": -5,
              "decimal": 3.14159,
              "scientific": 1.5e10,
              "trailingZeros": 10.0
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        assertThat(toon, containsString("zero: 0"));
        assertThat(toon, containsString("negative: -5"));
        assertThat(toon, containsString("decimal: 3.14159"));
        // Scientific notation should be converted to plain
        assertThat(toon, containsString("scientific: 15000000000"));
        // Trailing zeros should be stripped
        assertThat(toon, containsString("trailingZeros: 10"));
    }
}
