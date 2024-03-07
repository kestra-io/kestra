package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Requires(property = "kestra.variables.recursive-rendering", value = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class RenderFunction implements Function {
    @Inject
    private ApplicationContext applicationContext;

    public List<String> getArgumentNames() {
        return List.of("toRender", "recursive");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("toRender")) {
            throw new PebbleException(null, "The 'render' function expects an argument 'toRender'.", lineNumber, self.getName());
        }
        Object toRender = args.get("toRender");

        Object recursiveArg = args.get("recursive");
        if (recursiveArg == null) {
            recursiveArg = true;
        }

        if (!(recursiveArg instanceof Boolean recursive)) {
            throw new PebbleException(null, "The 'render' function expects an optional argument 'recursive' with type boolean.", lineNumber, self.getName());
        }

        EvaluationContextImpl evaluationContext = (EvaluationContextImpl) context;
        Map<String, Object> variables = evaluationContext.getScopeChain().getGlobalScopes().stream()
            .flatMap(scope -> scope.getKeys().stream())
            .distinct()
            .collect(HashMap::new, (m, v) -> m.put(v, context.getVariable(v)), HashMap::putAll);

        VariableRenderer variableRenderer = applicationContext.getBean(VariableRenderer.class);

        try {
            return variableRenderer.renderObject(toRender, variables, recursive).orElse(null);
        } catch (IllegalVariableEvaluationException e) {
            throw new PebbleException(e, e.getMessage());
        }
    }
}
