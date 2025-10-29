package io.kestra.core.runners.pebble.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.kestra.core.serializers.JacksonMapper;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JqFilter implements Filter {
    private static final Scope scope;

    static {
        scope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
    }

    private final Scope rootScope = Scope.newChildScope(scope);

    private final List<String> argumentNames = new ArrayList<>();

    public JqFilter() {
        this.argumentNames.add("expression");
    }

    @Override
    public List<String> getArgumentNames() {
        return this.argumentNames;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!args.containsKey("expression")) {
            throw new PebbleException(null, "The 'jq' filter expects an argument 'expression'.", lineNumber, self.getName());
        }

        String pattern = (String) args.get("expression");

        try {

            JsonQuery q = JsonQuery.compile(pattern, Versions.JQ_1_6);

            JsonNode in;
            if (input instanceof String stringValue) {
                in = JacksonMapper.ofJson().readTree(stringValue);
            } else {
                in = JacksonMapper.ofJson().valueToTree(input);
            }

            final List<Object> out = new ArrayList<>();

            try {
                q.apply(rootScope, in, v -> {
                    if (v instanceof TextNode) {
                        out.add(v.textValue());
                    } else if (v instanceof NullNode) {
                        out.add(null);
                    } else if (v instanceof NumericNode) {
                        out.add(v.numberValue());
                    } else if (v instanceof BooleanNode) {
                        out.add(v.booleanValue());
                    } else if (v instanceof ObjectNode) {
                        out.add(JacksonMapper.ofJson().convertValue(v, Map.class));
                    } else if (v instanceof ArrayNode) {
                        out.add(JacksonMapper.ofJson().convertValue(v, List.class));
                    } else {
                        out.add(v);
                    }
                });
            } catch (Exception e) {
                throw new Exception("Failed to resolve JQ expression '" + pattern + "' and value '" + input + "'", e);
            }

            return out;
        } catch (Exception e) {
            throw new PebbleException(e, "Unable to parse jq value '" + input + "' with type '" + input.getClass().getName() + "'", lineNumber, self.getName());
        }
    }
}
