package io.kestra.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.kestra.core.serializers.JacksonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal TOON reader, used only by {@link ToonUtilsTest} to prove that {@link ToonUtils#jsonToToon}
 * is lossless.
 *
 * <p>TOON encodes structure purely through indentation, so a substring assertion cannot tell a
 * correct document from one whose nesting has collapsed. Decoding the output and comparing it back
 * to the source {@code JsonNode} is the only assertion that actually covers that, which is why this
 * lives in the test source set rather than alongside the encoder.</p>
 *
 * <p>It deliberately understands only what the encoder emits and throws on anything else.</p>
 */
final class ToonDecoder {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$");

    private final List<Line> lines;
    private int pos;

    private record Line(int indent, String text) {}

    private ToonDecoder(List<Line> lines) {
        this.lines = lines;
    }

    static JsonNode decode(String toon) {
        List<Line> lines = new ArrayList<>();

        if (!toon.isEmpty()) {
            for (String raw : toon.split("\n", -1)) {
                int spaces = 0;
                while (spaces < raw.length() && raw.charAt(spaces) == ' ') {
                    spaces++;
                }
                if (spaces % 2 != 0) {
                    throw new IllegalStateException("Indentation is not a multiple of 2: [" + raw + "]");
                }
                lines.add(new Line(spaces / 2, raw.substring(spaces)));
            }
        }

        ToonDecoder decoder = new ToonDecoder(lines);
        JsonNode root = decoder.parseRoot();

        if (decoder.pos != lines.size()) {
            throw new IllegalStateException("Trailing content at line " + decoder.pos + ": " + lines.get(decoder.pos).text());
        }
        return root;
    }

    private JsonNode parseRoot() {
        if (lines.isEmpty()) {
            return MAPPER.createObjectNode();
        }

        String first = lines.getFirst().text();

        if (first.startsWith("[")) {
            pos++;
            return parseArray(new Cursor(first), 1);
        }
        if (isEntry(first)) {
            return parseObject(0);
        }

        pos++;
        return scalar(first);
    }

    /** Parses consecutive {@code key: value} entries sitting at {@code indent}. */
    private ObjectNode parseObject(int indent) {
        ObjectNode object = MAPPER.createObjectNode();

        while (pos < lines.size() && lines.get(pos).indent() == indent && !isListItem(lines.get(pos).text())) {
            parseEntryInto(object, lines.get(pos++).text(), indent + 1);
        }

        return object;
    }

    /**
     * Parses one entry off {@code text}. Anything the entry nests -- an object body, list items,
     * tabular rows -- is read from the following lines at {@code childIndent}.
     */
    private void parseEntryInto(ObjectNode object, String text, int childIndent) {
        Cursor cursor = new Cursor(text);
        String key = readKey(cursor, ":[");

        if (cursor.hasMore() && cursor.peek() == '[') {
            object.set(key, parseArray(cursor, childIndent));
            return;
        }

        cursor.expect(':');

        if (cursor.hasMore()) {
            cursor.expect(' ');
            object.set(key, scalar(cursor.rest()));
        } else if (pos < lines.size() && lines.get(pos).indent() == childIndent && !isListItem(lines.get(pos).text())) {
            object.set(key, parseObject(childIndent));
        } else {
            object.set(key, MAPPER.createObjectNode());
        }
    }

    /**
     * Parses an array whose header -- {@code [N]:}, {@code [N]: a,b} or {@code [N]{f1,f2}:} -- is
     * under the cursor and whose header line has already been consumed.
     */
    private ArrayNode parseArray(Cursor cursor, int childIndent) {
        cursor.expect('[');
        StringBuilder digits = new StringBuilder();
        while (cursor.peek() != ']') {
            digits.append(cursor.next());
        }
        cursor.expect(']');
        int size = Integer.parseInt(digits.toString());

        ArrayNode array = MAPPER.createArrayNode();

        // Tabular form: a header of field names followed by one comma-separated row per element.
        if (cursor.hasMore() && cursor.peek() == '{') {
            cursor.expect('{');
            List<String> fields = new ArrayList<>();
            while (true) {
                fields.add(readKey(cursor, ",}"));
                if (cursor.peek() == ',') {
                    cursor.next();
                    continue;
                }
                break;
            }
            cursor.expect('}');
            cursor.expect(':');

            for (int i = 0; i < size; i++) {
                Line row = take(childIndent);
                List<String> cells = splitCells(row.text());
                if (cells.size() != fields.size()) {
                    throw new IllegalStateException("Row has " + cells.size() + " cells but header declares " + fields.size() + ": " + row.text());
                }
                ObjectNode element = MAPPER.createObjectNode();
                for (int f = 0; f < fields.size(); f++) {
                    element.set(fields.get(f), scalar(cells.get(f)));
                }
                array.add(element);
            }
            return array;
        }

        cursor.expect(':');

        // Inline form: every element is a primitive on the header line.
        if (cursor.hasMore()) {
            cursor.expect(' ');
            for (String cell : splitCells(cursor.rest())) {
                array.add(scalar(cell));
            }
            return array;
        }

        if (size > 0) {
            parseListItems(array, childIndent, size);
        }
        return array;
    }

    /** Parses {@code size} list items, each a {@code - ...} line at {@code itemIndent}. */
    private void parseListItems(ArrayNode array, int itemIndent, int size) {
        for (int i = 0; i < size; i++) {
            Line line = take(itemIndent);
            String text = line.text();

            if (text.equals("-")) {
                array.add(MAPPER.createObjectNode());
                continue;
            }
            if (!text.startsWith("- ")) {
                throw new IllegalStateException("Expected a list item at indent " + itemIndent + ", found: " + text);
            }

            String body = text.substring(2);
            // The "- " marker shifts the item's first field two columns right, so whatever that
            // field nests belongs one further level in than the item's own sibling fields.
            int childIndent = itemIndent + 2;

            if (body.startsWith("[")) {
                array.add(parseArray(new Cursor(body), childIndent));
            } else if (isEntry(body)) {
                ObjectNode element = MAPPER.createObjectNode();
                parseEntryInto(element, body, childIndent);
                element.setAll(parseObject(itemIndent + 1));
                array.add(element);
            } else {
                array.add(scalar(body));
            }
        }
    }

    private Line take(int expectedIndent) {
        if (pos >= lines.size()) {
            throw new IllegalStateException("Unexpected end of document, expected a line at indent " + expectedIndent);
        }
        Line line = lines.get(pos++);
        if (line.indent() != expectedIndent) {
            throw new IllegalStateException("Expected indent " + expectedIndent + " but found " + line.indent() + ": " + line.text());
        }
        return line;
    }

    private static boolean isListItem(String text) {
        return text.equals("-") || text.startsWith("- ");
    }

    /** Whether {@code text} opens a {@code key:} / {@code key[...]} entry rather than a bare scalar. */
    private static boolean isEntry(String text) {
        Cursor cursor = new Cursor(text);
        try {
            readKey(cursor, ":[");
        } catch (IllegalStateException e) {
            return false;
        }
        return cursor.hasMore() && (cursor.peek() == ':' || cursor.peek() == '[');
    }

    private static String readKey(Cursor cursor, String stops) {
        if (cursor.peek() == '"') {
            return readQuoted(cursor);
        }
        StringBuilder key = new StringBuilder();
        while (cursor.hasMore() && stops.indexOf(cursor.peek()) < 0) {
            key.append(cursor.next());
        }
        return key.toString();
    }

    private static String readQuoted(Cursor cursor) {
        cursor.expect('"');
        StringBuilder value = new StringBuilder();

        while (true) {
            if (!cursor.hasMore()) {
                throw new IllegalStateException("Unterminated quoted token");
            }
            char c = cursor.next();
            if (c == '"') {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            char escaped = cursor.next();
            value.append(switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> throw new IllegalStateException("Unknown escape: \\" + escaped);
            });
        }
    }

    /** Splits a comma-separated cell list, ignoring commas inside quoted cells. */
    private static List<String> splitCells(String text) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (quoted) {
                cell.append(c);
                if (c == '\\' && i + 1 < text.length()) {
                    cell.append(text.charAt(++i));
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
                cell.append(c);
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }

        cells.add(cell.toString());
        return cells;
    }

    private static JsonNode scalar(String token) {
        if (token.startsWith("\"")) {
            return TextNode.valueOf(readQuoted(new Cursor(token)));
        }
        return switch (token) {
            case "null" -> NullNode.getInstance();
            case "true" -> BooleanNode.TRUE;
            case "false" -> BooleanNode.FALSE;
            default -> NUMBER.matcher(token).matches()
                ? DecimalNode.valueOf(new BigDecimal(token))
                : TextNode.valueOf(token);
        };
    }

    private static final class Cursor {
        private final String text;
        private int index;

        Cursor(String text) {
            this.text = text;
        }

        boolean hasMore() {
            return index < text.length();
        }

        char peek() {
            if (!hasMore()) {
                throw new IllegalStateException("Unexpected end of line: " + text);
            }
            return text.charAt(index);
        }

        char next() {
            char c = peek();
            index++;
            return c;
        }

        void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw new IllegalStateException("Expected '" + expected + "' but found '" + c + "' in: " + text);
            }
        }

        String rest() {
            return text.substring(index);
        }
    }
}
