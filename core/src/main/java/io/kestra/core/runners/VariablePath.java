package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A path into the variables of a run context, written the way Pebble reads it:
 * {@code outputs.fetch.rows[0].code} or {@code outputs.loop["a value"].code}.
 */
public record VariablePath(List<Object> segments) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public VariablePath {
        if (segments.isEmpty() || !(segments.getFirst() instanceof String)) {
            throw new IllegalArgumentException("A variable path must start with a variable name.");
        }
        segments = List.copyOf(segments);
    }

    public static VariablePath of(Object... segments) {
        return new VariablePath(List.of(segments));
    }

    /**
     * @throws IllegalArgumentException if the key is not a plain path of names and literal indexes
     */
    public static VariablePath parse(String key) {
        List<Object> segments = new ArrayList<>();
        int i = 0;
        while (i < key.length()) {
            char c = key.charAt(i);
            if (c == '[') {
                if (segments.isEmpty() || i + 1 >= key.length()) {
                    throw invalid(key);
                }
                int end = key.charAt(i + 1) == '"' ? readQuoted(key, i + 1, segments) : readIndex(key, i + 1, segments);
                if (end >= key.length() || key.charAt(end) != ']') {
                    throw invalid(key);
                }
                i = end + 1;
            } else {
                if (!segments.isEmpty()) {
                    if (c != '.') {
                        throw invalid(key);
                    }
                    i++;
                }
                int start = i;
                while (i < key.length() && key.charAt(i) != '.' && key.charAt(i) != '[') {
                    i++;
                }
                String name = key.substring(start, i);
                if (!IDENTIFIER.matcher(name).matches()) {
                    throw invalid(key);
                }
                segments.add(name);
            }
        }
        if (segments.isEmpty()) {
            throw invalid(key);
        }
        return new VariablePath(segments);
    }

    public String root() {
        return (String) segments.getFirst();
    }

    public int size() {
        return segments.size();
    }

    public Optional<Object> resolve(Map<String, Object> variables) {
        Object current = variables;
        for (Object segment : segments) {
            current = child(current, segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /**
     * Returns a copy of the variables with the value set at this path; only the containers on the path are copied.
     *
     * @throws IllegalArgumentException if a container on the path is neither a map nor a list holding the index
     */
    public Map<String, Object> set(Map<String, Object> variables, Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) setIn(variables, 0, value);
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(root());
        for (Object segment : segments.subList(1, segments.size())) {
            if (segment instanceof Number) {
                builder.append('[').append(segment).append(']');
            } else if (IDENTIFIER.matcher(segment.toString()).matches()) {
                builder.append('.').append(segment);
            } else {
                builder.append("[\"").append(segment.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]");
            }
        }
        return builder.toString();
    }

    private Object setIn(Object container, int index, Object value) {
        Object segment = segments.get(index);
        Object next = index + 1 == segments.size() ? value : setIn(childOrEmpty(container, segment), index + 1, value);

        if (container instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new HashMap<>(map);
            copy.put(segment.toString(), next);
            return copy;
        }
        if (container instanceof List<?> list && segment instanceof Number number && number.intValue() >= 0 && number.intValue() < list.size()) {
            List<Object> copy = new ArrayList<>(list);
            copy.set(number.intValue(), next);
            return copy;
        }
        throw new IllegalArgumentException("Cannot set a value at '%s': '%s' is not a map or a list holding this index.".formatted(this, segment));
    }

    private static Object childOrEmpty(Object container, Object segment) {
        Object child = child(container, segment);
        return child == null ? Map.of() : child;
    }

    private static Object child(Object container, Object segment) {
        if (container instanceof Map<?, ?> map) {
            return map.get(segment.toString());
        }
        if (container instanceof List<?> list && segment instanceof Number number) {
            int index = number.intValue();
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private static int readIndex(String key, int start, List<Object> segments) {
        int end = start;
        while (end < key.length() && Character.isDigit(key.charAt(end))) {
            end++;
        }
        if (end == start) {
            throw invalid(key);
        }
        segments.add(Integer.parseInt(key.substring(start, end)));
        return end;
    }

    private static int readQuoted(String key, int start, List<Object> segments) {
        StringBuilder value = new StringBuilder();
        int i = start + 1;
        while (i < key.length() && key.charAt(i) != '"') {
            if (key.charAt(i) == '\\' && i + 1 < key.length()) {
                i++;
            }
            value.append(key.charAt(i));
            i++;
        }
        if (i >= key.length()) {
            throw invalid(key);
        }
        segments.add(value.toString());
        return i + 1;
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Invalid variable path '%s': expected names and literal indexes, such as outputs.task.rows[0].name.".formatted(key));
    }
}
