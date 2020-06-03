package org.kestra.core.runners.handlebars.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import lombok.SneakyThrows;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import org.kestra.core.serializers.JacksonMapper;

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

        if (value instanceof String) {
            in = JacksonMapper.ofJson().readTree((String) value);
        } else  {
            in = JacksonMapper.ofJson().valueToTree(value);
        }

        final List<JsonNode> out = new ArrayList<>();
        q.apply(scope, in, out::add);

        if (first) {
            if (out.size() > 0) {
                return JacksonMapper.ofJson().writeValueAsString(out.get(0));
            } else {
                return JacksonMapper.ofJson().writeValueAsString(out);
            }
        } else {
            return JacksonMapper.ofJson().writeValueAsString(out);
        }

    }
}

