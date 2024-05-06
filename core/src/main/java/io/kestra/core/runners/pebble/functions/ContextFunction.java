package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ContextFunction implements Function {
    public List<String> getArgumentNames() {
        return List.of();
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return ((EvaluationContextImpl) context)
            .getScopeChain()
            .getGlobalScopes()
            .stream()
            // remove global one created by pebble
            .filter(scope -> !scope.getKeys().contains("_context"))
            .flatMap(scope -> scope.getKeys().stream())
            .distinct()
            .collect(HashMap::new, (m, v) -> m.put(v, context.getVariable(v)), HashMap::putAll);
    }
}
