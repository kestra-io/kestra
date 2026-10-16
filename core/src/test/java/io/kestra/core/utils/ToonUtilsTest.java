package io.kestra.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToonUtilsTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void simpleObject() throws Exception {
        assertToon("""
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                }
              }
            }
            """, """
            type: object
            properties:
              name:
                type: string""");
    }

    @Test
    void nestedObject() throws Exception {
        assertToon("""
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
            """, """
            type: object
            properties:
              user:
                type: object
                properties:
                  name:
                    type: string
                  age:
                    type: integer""");
    }

    @Test
    void primitiveArray() throws Exception {
        assertToon("""
            {
              "required": ["id", "name", "type"]
            }
            """, "required[3]: id,name,type");
    }

    @Test
    void emptyArray() throws Exception {
        assertToon("""
            {
              "items": []
            }
            """, "items[0]:");
    }

    @Test
    void uniformObjectArrayUsesTabularForm() throws Exception {
        assertToon("""
            {
              "users": [
                {"id": 1, "name": "Alice"},
                {"id": 2, "name": "Bob"}
              ]
            }
            """, """
            users[2]{id,name}:
              1,Alice
              2,Bob""");
    }

    @Test
    void nonUniformObjectArrayUsesListForm() throws Exception {
        assertToon("""
            {
              "items": [
                {"type": "string"},
                {"type": "integer", "minimum": 0}
              ]
            }
            """, """
            items[2]:
              - type: string
              - type: integer
                minimum: 0""");
    }

    /**
     * Regression: the `- ` marker already shifts the first key two columns right, so an
     * object-valued first field used to emit its children at the marker's own level, flattening
     * them into siblings of the key they belong to.
     */
    @Test
    void objectAsFirstFieldOfListItemKeepsItsNesting() throws Exception {
        assertToon("""
            {
              "anyOf": [
                {"props": {"a": 1, "b": 2}, "second": "x"}
              ]
            }
            """, """
            anyOf[1]:
              - props:
                  a: 1
                  b: 2
                second: x""");
    }

    /** Regression: an array-valued first field used to emit a second, orphaned `[N]:` header. */
    @Test
    void arrayAsFirstFieldOfListItemEmitsASingleHeader() throws Exception {
        assertToon("""
            {
              "anyOf": [
                {"items": [{"x": 1}, {"y": 2}], "tail": "t"}
              ]
            }
            """, """
            anyOf[1]:
              - items[2]:
                  - x: 1
                  - y: 2
                tail: t""");
    }

    @Test
    void nestedArraysOfPrimitives() throws Exception {
        assertToon("""
            {
              "matrix": [[1, 2], [3, 4]]
            }
            """, """
            matrix[2]:
              - [2]: 1,2
              - [2]: 3,4""");
    }

    /** Regression: a nested array of objects used to indent its contents one level short. */
    @Test
    void nestedArrayOfObjects() throws Exception {
        assertToon("""
            {
              "matrix": [[{"x": 1}, {"y": 2}]]
            }
            """, """
            matrix[1]:
              - [2]:
                  - x: 1
                  - y: 2""");
    }

    @Test
    void emptyObjectsInAListItem() throws Exception {
        assertToon("""
            {
              "anyOf": [{}, {}]
            }
            """, """
            anyOf[2]:
              -
              -""");
    }

    @Test
    void primitiveValues() throws Exception {
        assertToon("""
            {
              "string": "hello",
              "integer": 42,
              "decimal": 3.14,
              "boolean": true,
              "nullValue": null
            }
            """, """
            string: hello
            integer: 42
            decimal: 3.14
            boolean: true
            nullValue: null""");
    }

    @Test
    void stringsWithSpecialCharactersAreQuotedAndEscaped() throws Exception {
        assertToon("""
            {
              "withColon": "key:value",
              "withQuotes": "say \\"hello\\"",
              "withNewline": "line1\\nline2",
              "withComma": "a,b,c",
              "withBracket": "a[0]"
            }
            """, """
            withColon: "key:value"
            withQuotes: "say \\"hello\\""
            withNewline: "line1\\nline2"
            withComma: "a,b,c"
            withBracket: "a[0]\"""");
    }

    /**
     * Regression: surrounding whitespace is not recoverable once emitted bare, and a leading '#'
     * is ambiguous with a comment marker -- both used to be written unquoted.
     */
    @Test
    void ambiguousStringsAreQuoted() throws Exception {
        assertToon("""
            {
              "leading": "  padded",
              "trailing": "padded  ",
              "ref": "#/definitions/Task",
              "innerDash": "a-b",
              "leadingDash": "-x"
            }
            """, """
            leading: "  padded"
            trailing: "padded  "
            ref: "#/definitions/Task"
            innerDash: a-b
            leadingDash: "-x\"""");
    }

    @Test
    void stringsThatLookLikeKeywordsOrNumbersAreQuoted() throws Exception {
        assertToon("""
            {
              "truthValue": "true",
              "falseValue": "false",
              "nullString": "null",
              "numeric": "42",
              "leadingZero": "007",
              "empty": ""
            }
            """, """
            truthValue: "true"
            falseValue: "false"
            nullString: "null"
            numeric: "42"
            leadingZero: "007"
            empty: \"\"""");
    }

    @Test
    void keysAreQuotedOnlyWhenNecessary() throws Exception {
        assertToon("""
            {
              "simple": "value",
              "with.dot": "value",
              "with-dash": "value",
              "with space": "value",
              "$special": "value"
            }
            """, """
            simple: value
            with.dot: value
            "with-dash": value
            "with space": value
            "$special": value""");
    }

    @Test
    void numbersAreNormalisedToPlainForm() throws Exception {
        assertToon("""
            {
              "zero": 0,
              "negative": -5,
              "decimal": 3.14159,
              "scientific": 1.5e10,
              "trailingZeros": 10.0
            }
            """, """
            zero: 0
            negative: -5
            decimal: 3.14159
            scientific: 15000000000
            trailingZeros: 10""");
    }

    @Test
    void jsonSchemaShape() throws Exception {
        assertToon("""
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
            """, """
            type: object
            properties:
              id:
                type: string
                description: Unique identifier
              tasks:
                type: array
                items:
                  type: object
                  properties:
                    type:
                      type: string
            required[1]: id""");
    }

    @Test
    void emptyObjectProducesAnEmptyDocument() throws Exception {
        assertToon("{}", "");
    }

    @Test
    void rootLevelValues() throws Exception {
        assertToon("\"hello\"", "hello");
        assertToon("42", "42");
        assertToon("[1, 2, 3]", "[3]: 1,2,3");
    }

    @Test
    void nullInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ToonUtils.jsonToToon(null));
    }

    /**
     * TOON's compaction comes almost entirely from the tabular form, so it depends on the shape of
     * the document rather than on its size. Indentation costs two characters per nesting level per
     * line, which cancels the saved braces and quotes once a document is deeply nested -- the shape
     * a JSON Schema actually has.
     *
     * <p>These are character counts, not token counts. Before relying on either number, measure the
     * real generated Flow schema with the tokenizer of the model in use.</p>
     */
    @Test
    void compactionDependsOnDocumentShape() throws Exception {
        StringBuilder rows = new StringBuilder();
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            rows.append(i > 0 ? "," : "").append("{\"id\":%d,\"name\":\"task%d\",\"enabled\":true}".formatted(i, i));
            properties.append(i > 0 ? "," : "").append("\"p%d\":{\"type\":\"string\",\"description\":\"prop %d\"}".formatted(i, i));
        }

        // A uniform array of objects collapses into the tabular form: a large, real saving.
        assertThat(savingPercent("{\"rows\":[" + rows + "]}"), greaterThan(40.0));

        // The same data expressed as a nested schema saves nothing. The assertion only guards
        // against the encoder inflating the payload; it is deliberately not a claimed win.
        assertThat(savingPercent("{\"type\":\"object\",\"properties\":{" + properties + "}}"), greaterThan(-10.0));
    }

    private static double savingPercent(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        int minified = MAPPER.writeValueAsString(node).length();
        return (1.0 - (double) ToonUtils.jsonToToon(node).length() / minified) * 100;
    }

    /**
     * The only assertion that genuinely covers an indentation-based format: encode, decode, and
     * check nothing was lost. Substring assertions cannot distinguish a correct document from one
     * whose nesting has collapsed.
     */
    @ParameterizedTest
    @MethodSource("roundTripDocuments")
    void roundTripsWithoutLoss(String json) throws Exception {
        assertRoundTrip(MAPPER.readTree(json));
    }

    static Stream<String> roundTripDocuments() {
        return Stream.of(
            "{}",
            "[]",
            "\"hello\"",
            "42",
            """
            {"a": {}, "b": {"c": {}}, "d": null}""",
            """
            {"anyOf": [{"props": {"a": 1, "b": {"deep": true}}, "second": "x"}]}""",
            """
            {"anyOf": [{"items": [{"x": 1}, {"y": 2}], "tail": "t"}]}""",
            """
            {"matrix": [[{"x": 1}], [[1, 2], []], [], 7]}""",
            """
            {"rows": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob,Jr"}]}""",
            """
            {"mixed": [1, "two", null, true, {"k": "v"}, [3]]}""",
            """
            {"odd keys": {"with-dash": 1, "$ref": "#/x", "a.b": 2}}""",
            """
            {"tricky": ["  pad  ", "", "true", "42", "007", "-x", "a:b", "a,b", "say \\"hi\\"", "l1\\nl2", "#c"]}""",
            """
            {"numbers": [0, 10.0, 1.5e10, -5, 3.14159]}""",
            """
            {"type": "object", "properties": {"tasks": {"type": "array", "items": {"anyOf": [
              {"$ref": "#/defs/Log"},
              {"type": "object", "properties": {"id": {"type": "string"}}, "required": ["id"]}
            ]}}}}"""
        );
    }

    private static void assertToon(String json, String expectedToon) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        assertThat(ToonUtils.jsonToToon(node), is(expectedToon));
        assertRoundTrip(node);
    }

    private static void assertRoundTrip(JsonNode original) {
        String toon = ToonUtils.jsonToToon(original);
        JsonNode decoded = ToonDecoder.decode(toon);

        assertThat(
            "round-trip lost data\n--- toon ---\n" + toon + "\n--- decoded ---\n" + decoded.toPrettyString(),
            equivalent(original, decoded),
            is(true)
        );
    }

    /** Structural equality, comparing numbers by value so 10.0 and 10 match. */
    private static boolean equivalent(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return false;
        }
        if (expected.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (var entry : expected.properties()) {
                JsonNode other = actual.get(entry.getKey());
                if (other == null || !equivalent(entry.getValue(), other)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!equivalent(expected.get(i), actual.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }
}
