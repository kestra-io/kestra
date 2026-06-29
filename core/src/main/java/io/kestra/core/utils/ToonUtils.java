package io.kestra.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.*;

/**
 * Utility class for converting JSON to TOON (Tree-structured Object Operational Notation) format.
 * TOON is an indentation-based text format that efficiently encodes JSON data with minimal quoting,
 * optimized for LLM token reduction and readability.
 * 
 * <p>TOON Format Overview:</p>
 * <ul>
 *   <li>Objects: key-value pairs with indentation</li>
 *   <li>Arrays: [N]: notation with items indented</li>
 *   <li>Primitives: minimal quoting (numbers, booleans, null unquoted)</li>
 * </ul>
 * 
 * <p>Example:</p>
 * <pre>
 * type: object
 * properties:
 *   id:
 *     type: string
 *   items[2]:
 *     - name: Item1
 *       price: 10.5
 *     - name: Item2
 *       price: 20.0
 * </pre>
 * 
 * @see <a href="https://github.com/toon-format/spec">TOON Specification 2.0</a>
 */
public class ToonUtils {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String INDENT_UNIT = "  "; // 2 spaces
    private static final char DOCUMENT_DELIMITER = ',';

    /**
     * Convert a JsonNode to TOON format string.
     * 
     * @param node The JsonNode to convert (typically a JSON Schema object)
     * @return TOON formatted string representation
     * @throws IllegalArgumentException if node is null or conversion fails
     */
    public static String jsonToToon(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("JsonNode cannot be null");
        }

        try {
            StringWriter writer = new StringWriter();
            ToonEncoder encoder = new ToonEncoder(writer);

            if (node.isArray()) {
                encoder.writeRootArray(node);
            } else if (node.isObject()) {
                encoder.writeRootObject(node);
            } else {
                encoder.writeRootPrimitive(node);
            }

            return writer.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to convert JsonNode to TOON format", e);
        }
    }

    /**
     * Internal encoder for writing JSON nodes as TOON format.
     */
    private static class ToonEncoder {
        private final Writer writer;
        private boolean firstLine = true;

        ToonEncoder(Writer writer) {
            this.writer = writer;
        }

        /* ---------- Root entry points ---------- */

        void writeRootObject(JsonNode node) throws IOException {
            if (node.isEmpty()) {
                return;
            }
            writeObject(node, 0);
        }

        void writeRootArray(JsonNode array) throws IOException {
            writeArray(array, 0, null);
        }

        void writeRootPrimitive(JsonNode node) throws IOException {
            String value = formatPrimitive(node);
            writeLine(0, value);
        }

        /* ---------- Core writing helpers ---------- */

        private void writeObject(JsonNode node, int indent) throws IOException {
            for (var entry : node.properties()) {
                String key = formatKey(entry.getKey());
                JsonNode value = entry.getValue();

                if (value.isObject()) {
                    writeLine(indent, key + ":");
                    if (!value.isEmpty()) {
                        writeObject(value, indent + 1);
                    }
                } else if (value.isArray()) {
                    writeFieldArray(key, value, indent);
                } else {
                    String v = formatPrimitive(value);
                    writeLine(indent, key + ": " + v);
                }
            }
        }

        private void writeArray(JsonNode array, int indent, String key) throws IOException {
            int size = array.size();

            // Empty array
            if (size == 0) {
                String header = headerPrefix(key) + "[" + size + "]:";
                writeLine(indent, header);
                return;
            }

            boolean allPrimitive = true;
            boolean allObjects = true;

            for (JsonNode item : array) {
                if (item.isObject() || item.isArray()) {
                    allPrimitive = false;
                }
                if (!item.isObject()) {
                    allObjects = false;
                }
            }

            // Inline primitive arrays
            if (allPrimitive) {
                writePrimitiveArray(array, indent, key);
                return;
            }

            // Uniform object arrays (tabular form)
            if (allObjects && isUniformPrimitiveObjectArray(array)) {
                writeTabularArray(array, indent, key);
                return;
            }

            // List array (mixed or nested)
            writeListArray(array, indent, key);
        }

        private void writePrimitiveArray(JsonNode array, int indent, String key) throws IOException {
            int size = array.size();
            StringBuilder line = new StringBuilder();
            line.append(headerPrefix(key))
                .append("[")
                .append(size)
                .append("]:");

            if (size > 0) {
                line.append(' ');
                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        line.append(DOCUMENT_DELIMITER);
                    }
                    line.append(formatPrimitive(array.get(i)));
                }
            }
            writeLine(indent, line.toString());
        }

        private boolean isUniformPrimitiveObjectArray(JsonNode array) {
            if (array.isEmpty()) {
                return false;
            }

            Set<String> fields = new LinkedHashSet<>();
            array.get(0).properties().forEach(entry -> fields.add(entry.getKey()));

            if (fields.isEmpty()) {
                return false;
            }

            for (JsonNode item : array) {
                Set<String> keys = new LinkedHashSet<>();
                item.properties().forEach(entry -> keys.add(entry.getKey()));

                if (!keys.equals(fields)) {
                    return false;
                }

                // All values must be primitives
                for (var entry : item.properties()) {
                    if (entry.getValue().isObject() || entry.getValue().isArray()) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void writeTabularArray(JsonNode array, int indent, String key) throws IOException {
            int size = array.size();

            // Collect ordered field names from first object
            Set<String> fields = new LinkedHashSet<>();
            array.get(0).properties().forEach(entry -> fields.add(entry.getKey()));

            StringBuilder header = new StringBuilder();
            header.append(headerPrefix(key))
                .append("[")
                .append(size)
                .append("]{");

            boolean first = true;
            for (String field : fields) {
                if (!first) {
                    header.append(DOCUMENT_DELIMITER);
                }
                header.append(formatKey(field));
                first = false;
            }
            header.append("}:");
            writeLine(indent, header.toString());

            // Rows at depth +1
            for (JsonNode row : array) {
                StringBuilder rowLine = new StringBuilder();
                boolean firstCell = true;
                for (String field : fields) {
                    if (!firstCell) {
                        rowLine.append(DOCUMENT_DELIMITER);
                    }
                    JsonNode cell = row.get(field);
                    rowLine.append(formatPrimitive(cell == null ? nullNode() : cell));
                    firstCell = false;
                }
                writeLine(indent + 1, rowLine.toString());
            }
        }

        private void writeListArray(JsonNode array, int indent, String key) throws IOException {
            int size = array.size();
            String header = headerPrefix(key) + "[" + size + "]:";
            writeLine(indent, header);

            // Each element rendered as a list item
            for (JsonNode item : array) {
                if (item.isObject()) {
                    writeListObjectItem(item, indent);
                } else if (item.isArray()) {
                    writeListArrayItem(item, indent);
                } else {
                    String v = formatPrimitive(item);
                    writeLine(indent + 1, "- " + v);
                }
            }
        }

        private void writeListObjectItem(JsonNode obj, int indent) throws IOException {
            var fieldsIter = obj.properties().iterator();
            if (!fieldsIter.hasNext()) {
                writeLine(indent + 1, "-");
                return;
            }

            var first = fieldsIter.next();
            String firstKey = formatKey(first.getKey());
            JsonNode firstValue = first.getValue();

            if (firstValue.isObject()) {
                writeLine(indent + 1, "- " + firstKey + ":");
                if (!firstValue.isEmpty()) {
                    writeObject(firstValue, indent + 2);
                }
            } else if (firstValue.isArray()) {
                writeListArrayFirstField(firstKey, firstValue, indent + 1);
            } else {
                String v = formatPrimitive(firstValue);
                writeLine(indent + 1, "- " + firstKey + ": " + v);
            }

            // Remaining fields at depth +2
            while (fieldsIter.hasNext()) {
                var entry = fieldsIter.next();
                String key = formatKey(entry.getKey());
                JsonNode value = entry.getValue();

                if (value.isObject()) {
                    writeLine(indent + 2, key + ":");
                    if (!value.isEmpty()) {
                        writeObject(value, indent + 3);
                    }
                } else if (value.isArray()) {
                    writeFieldArray(key, value, indent + 2);
                } else {
                    String v = formatPrimitive(value);
                    writeLine(indent + 2, key + ": " + v);
                }
            }
        }

        private void writeListArrayFirstField(String key, JsonNode array, int hyphenIndent) throws IOException {
            int size = array.size();
            boolean allPrimitive = true;

            for (JsonNode item : array) {
                if (item.isObject() || item.isArray()) {
                    allPrimitive = false;
                    break;
                }
            }

            if (allPrimitive) {
                StringBuilder line = new StringBuilder();
                line.append("- ")
                    .append(key)
                    .append("[")
                    .append(size)
                    .append("]:");

                if (size > 0) {
                    line.append(' ');
                    for (int i = 0; i < size; i++) {
                        if (i > 0) {
                            line.append(DOCUMENT_DELIMITER);
                        }
                        line.append(formatPrimitive(array.get(i)));
                    }
                }
                writeLine(hyphenIndent, line.toString());
            } else {
                String header = "- " + key + "[" + size + "]:";
                writeLine(hyphenIndent, header);
                writeListArray(array, hyphenIndent + 1, null);
            }
        }

        private void writeFieldArray(String key, JsonNode array, int indent) throws IOException {
            writeArray(array, indent, key);
        }

        private void writeListArrayItem(JsonNode arrayNode, int indent) throws IOException {
            int size = arrayNode.size();
            boolean allPrimitive = true;

            for (JsonNode item : arrayNode) {
                if (item.isObject() || item.isArray()) {
                    allPrimitive = false;
                    break;
                }
            }

            if (allPrimitive) {
                StringBuilder line = new StringBuilder();
                line.append("- [")
                    .append(size)
                    .append("]:");

                if (size > 0) {
                    line.append(' ');
                    for (int i = 0; i < size; i++) {
                        if (i > 0) {
                            line.append(DOCUMENT_DELIMITER);
                        }
                        line.append(formatPrimitive(arrayNode.get(i)));
                    }
                }
                writeLine(indent + 1, line.toString());
            } else {
                String header = "- [" + size + "]:";
                writeLine(indent + 1, header);

                for (JsonNode item : arrayNode) {
                    if (item.isObject()) {
                        writeListObjectItem(item, indent + 1);
                    } else if (item.isArray()) {
                        writeListArrayItem(item, indent + 1);
                    } else {
                        String v = formatPrimitive(item);
                        writeLine(indent + 2, "- " + v);
                    }
                }
            }
        }

        /* ---------- Formatting helpers ---------- */

        private String headerPrefix(String key) {
            return key == null ? "" : key;
        }

        private JsonNode nullNode() {
            return MAPPER.nullNode();
        }

        private String formatPrimitive(JsonNode node) {
            if (node == null || node.isNull()) {
                return "null";
            }
            if (node.isNumber()) {
                return formatNumber(node);
            }
            if (node.isBoolean()) {
                return node.booleanValue() ? "true" : "false";
            }

            String raw = node.textValue();
            return quoteStringIfNeeded(raw);
        }

        private String formatNumber(JsonNode node) {
            BigDecimal dec = node.decimalValue();
            if (dec.compareTo(BigDecimal.ZERO) == 0) {
                return "0";
            }
            String s = dec.stripTrailingZeros().toPlainString();
            if (s.equals("-0")) {
                return "0";
            }
            return s;
        }

        private String quoteStringIfNeeded(String value) {
            if (value == null) {
                return "null";
            }

            // Empty string
            if (value.isEmpty()) {
                return "\"\"";
            }

            // Reserved keywords
            if (value.equals("true") || value.equals("false") || value.equals("null")) {
                return "\"" + escape(value) + "\"";
            }

            // Numeric-like tokens
            if (value.matches("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$") || value.matches("^0\\d+$")) {
                return "\"" + escape(value) + "\"";
            }

            // Structural / special characters
            if (value.indexOf(':') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\\') >= 0) {
                return "\"" + escape(value) + "\"";
            }
            if (value.indexOf('[') >= 0 || value.indexOf(']') >= 0 ||
                value.indexOf('{') >= 0 || value.indexOf('}') >= 0) {
                return "\"" + escape(value) + "\"";
            }

            // Control characters
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0) {
                return "\"" + escape(value) + "\"";
            }

            // Document delimiter
            if (value.indexOf(DOCUMENT_DELIMITER) >= 0) {
                return "\"" + escape(value) + "\"";
            }

            // Strings equal "-" or starting with "-"
            if (value.equals("-") || value.startsWith("-")) {
                return "\"" + escape(value) + "\"";
            }

            // Safe to emit without quotes
            return value;
        }

        private String escape(String s) {
            return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        }

        private String formatKey(String key) {
            if (key.matches("^[A-Za-z_][A-Za-z0-9_.]*$")) {
                return key;
            }
            return "\"" + escape(key) + "\"";
        }

        /* ---------- Line / indentation helpers ---------- */

        private void writeLine(int indentLevel, String content) throws IOException {
            if (!firstLine) {
                writer.write("\n");
            }
            indent(indentLevel);
            writer.write(content);
            firstLine = false;
        }

        private void indent(int level) throws IOException {
            if (level <= 0) {
                return;
            }
            writer.write(INDENT_UNIT.repeat(level));
        }
    }
}
