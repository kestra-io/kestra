package io.kestra.runner.h2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.kestra.core.serializers.JacksonMapper;
import lombok.SneakyThrows;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class H2Functions {
    private static final Scope rootScope;
    private static final Scope scope;

    static {
        rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
        scope = Scope.newEmptyScope();
    }

    public static Boolean jqBoolean(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asBoolean);
    }

    public static String jqString(String value, String expression) {
        return H2Functions.jq(value, expression, JsonNode::asText);
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
    private static  <T> T jq(String value, String expression, Function<JsonNode, T> function) {
        JsonQuery q = JsonQuery.compile(expression, Versions.JQ_1_6);

        final List<JsonNode> out = new ArrayList<>();
        JsonNode in = JacksonMapper.ofJson().readTree(value);

        q.apply(scope, in, out::add);

        JsonNode node = out.get(0);

        if (node instanceof NullNode) {
            return null;
        } else {
            return function.apply(node);
        }
    }
}
