package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "kestra.variables.recursive-rendering", value = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class RenderOnceFunction extends RenderFunction {
    public static final String NAME = "renderOnce";
    public List<String> getArgumentNames() {
        return List.of("toRender");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of("toRender", "inputs.inputWithPebble");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Map<String, Object> argsWithNonRecursive = new HashMap<>(args);
        argsWithNonRecursive.put("recursive", false);

        return super.execute(argsWithNonRecursive, self, context, lineNumber);
    }

    @Override
    public String functionName() {
        return "renderOnce";
    }
}
