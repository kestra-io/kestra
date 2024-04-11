package io.kestra.core.runners.pebble.functions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Requires(property = "kestra.variables.recursive-rendering", value = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class RenderOnceFunction extends RenderFunction {
    public List<String> getArgumentNames() {
        return List.of("toRender");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Map<String, Object> argsWithNonRecursive = new HashMap<>(args);
        argsWithNonRecursive.put("recursive", false);

        return super.execute(argsWithNonRecursive, self, context, lineNumber);
    }
}
