package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.configuration.VariableConfiguration;

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

@Singleton
@Requires(property = "kestra.variables.recursive-rendering", value = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class RenderFunction implements Function, RenderingFunctionInterface {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private VariableConfiguration variableConfiguration;

    public List<String> getArgumentNames() {
        return List.of("toRender", "recursive");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        int depth = context.getVariable(VariableRenderer.RENDER_DEPTH_VAR) instanceof Number n ? n.intValue() : 0;
        int maxDepth = variableConfiguration.getMaxRenderDepth();
        if (depth >= maxDepth) {
            throw new PebbleException(null,
                "Maximum render() nesting depth (" + maxDepth + ") exceeded at line " + lineNumber +
                    " — check for circular render() calls in your template.",
                lineNumber, self.getName());
        }

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

        try {
            return ((RenderingFunctionInterface) evaluationContext.getExtensionRegistry().getFunction(functionName())).variableRenderer(applicationContext)
                .renderObject(toRender, variables, recursive, depth + 1).orElse(null);
        } catch (IllegalVariableEvaluationException e) {
            throw new PebbleException(e, e.getMessage());
        }
    }

    @Override
    public String functionName() {
        return "render";
    }
}
