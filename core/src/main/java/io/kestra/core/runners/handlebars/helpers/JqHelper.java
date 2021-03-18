package io.kestra.core.runners.handlebars.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import lombok.SneakyThrows;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import io.kestra.core.serializers.JacksonMapper;

import java.util.ArrayList;
import java.util.List;

public class JqHelper implements Helper<Object> {
    private final Scope scope;

    public JqHelper() {
        scope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
    }

    @SneakyThrows
    @Override
    public Object apply(final Object value, final Options options) {
        Scope rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);


        String pattern = options.param(0, options.hash("expr"));
        boolean first = options.param(1, options.hash("first", false));
        JsonQuery q = JsonQuery.compile(pattern, Versions.JQ_1_6);

        JsonNode in;

        try {
            if (value instanceof String) {
                in = JacksonMapper.ofJson().readTree((String) value);
            } else {
                in = JacksonMapper.ofJson().valueToTree(value);
            }
        } catch (Exception e) {
            throw new Exception("Unable to parse jq value '" + value +  "' with type '" + value.getClass().getName() + "'", e);
        }

        final List<JsonNode> out = new ArrayList<>();

        try {
            q.apply(scope, in, out::add);
        } catch (Exception e) {
            throw new Exception("Failed to resolve JQ expression '" + pattern +  "' and value '" + value +  "'", e);
        }

        if (first) {
            if (out.size() > 0) {
                if (out.get(0).getNodeType() == JsonNodeType.STRING) {
                    return out.get(0).asText();
                } else {
                    return JacksonMapper.ofJson().writeValueAsString(out.get(0));
                }
            } else {
                return JacksonMapper.ofJson().writeValueAsString(out);
            }
        } else {
            return JacksonMapper.ofJson().writeValueAsString(out);
        }

    }
}

