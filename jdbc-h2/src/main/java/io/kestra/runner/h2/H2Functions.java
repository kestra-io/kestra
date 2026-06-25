package io.kestra.runner.h2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.kestra.core.serializers.JacksonMapper;

import lombok.SneakyThrows;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

public final class H2Functions {

    private H2Functions() {}
    private static final Scope scope = Scope.newEmptyScope();
    private static final ConcurrentHashMap<String, JsonQuery> QUERY_CACHE = new ConcurrentHashMap<>();

    static {
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
    }

    /**
     * Escapes a value for safe embedding inside a jq string literal ({@code "..."}).
     * Follows JSON string escaping rules: backslash, double-quote, and all control
     * characters (U+0000–U+001F) are escaped so the resulting string can be safely
     * concatenated into a jq filter without altering the program structure.
     *
     * @param value the raw user-supplied key or value to embed in a jq string literal
     * @return the escaped string, or {@code null} if {@code value} is {@code null}
     */
    public static String escapeJqString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Backslash must be handled before double-quote to avoid double-escaping
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static Boolean jqBoolean(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asBoolean);
    }

    public static String jqString(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asText);
    }

    public static String[] jqStringArray(String value, String expression) {
        return H2Functions.jqArray(value, expression, JsonNode::asText)
            .toArray(String[]::new);
    }

    public static Long jqLong(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asLong);
    }

    public static Integer jqInteger(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asInt);
    }

    public static Double jqDouble(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asDouble);
    }

    @SneakyThrows
    private static List<JsonNode> jq(String value, String expression) {
        JsonQuery q = QUERY_CACHE.computeIfAbsent(expression, H2Functions::compileQuery);

        final List<JsonNode> out = new ArrayList<>();
        JsonNode in = JacksonMapper.ofJson().readTree(value);

        q.apply(scope, in, out::add);

        return out;
    }

    @SneakyThrows
    private static JsonQuery compileQuery(String expression) {
        return JsonQuery.compile(expression, Versions.JQ_1_6);
    }

    @SneakyThrows
    private static <T> T jq(String value, String expression, Function<JsonNode, T> function) {
        List<JsonNode> jq = H2Functions.jq(value, expression);
        if (jq.isEmpty()) {
            return null;
        }
        JsonNode node = jq.getFirst();

        if (node instanceof NullNode) {
            return null;
        } else {
            return function.apply(node);
        }
    }

    @SneakyThrows
    private static <T> List<T> jqArray(String value, String expression, Function<JsonNode, T> function) {
        JsonNode node = H2Functions.jq(value, expression).getFirst();

        if (!(node instanceof ArrayNode)) {
            return List.of();
        }

        return StreamSupport
            .stream(node.spliterator(), false)
            .map(function::apply)
            .toList();
    }
}
