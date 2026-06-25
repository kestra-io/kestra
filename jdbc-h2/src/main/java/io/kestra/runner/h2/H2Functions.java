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

public class H2Functions {
    private static final Scope scope = Scope.newEmptyScope();
    private static final ConcurrentHashMap<String, JsonQuery> QUERY_CACHE = new ConcurrentHashMap<>();

    static {
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
    }

    /**
     * Escapes a value for safe embedding inside a jq string literal ({@code "..."}).
     * Escapes backslashes first, then double-quotes, so the resulting string can be
     * safely concatenated into a jq filter without altering the program structure.
     *
     * @param value the raw user-supplied key or value to embed in a jq string literal
     * @return the escaped string, or {@code null} if {@code value} is {@code null}
     */
    public static String escapeJqString(String value) {
        if (value == null) {
            return null;
        }
        // Backslash must be escaped before double-quote to avoid double-escaping
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
