package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RenderFunction implements Function {
    public List<String> getArgumentNames() {
        return List.of("template", "recursive");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("template")) {
            throw new PebbleException(null, "The 'render' function expects an argument 'template'.", lineNumber, self.getName());
        }

        if (!(args.get("template") instanceof String)) {
            throw new PebbleException(null, "The 'render' function expects an argument 'template' with type string.", lineNumber, self.getName());
        }
        String template = (String) args.get("template");

        Object recursiveArg = args.get("recursive");
        if (recursiveArg == null) {
            recursiveArg = true;
        }

        if (!(recursiveArg instanceof Boolean)) {
            throw new PebbleException(null, "The 'render' function expects an optional argument 'recursive' with type boolean.", lineNumber, self.getName());
        }
        Boolean recursive = (Boolean) recursiveArg;

        EvaluationContextImpl evaluationContext = (EvaluationContextImpl) context;
        Map<String, Object> variables = evaluationContext.getScopeChain().getGlobalScopes().stream()
            .flatMap(scope -> scope.getKeys().stream())
            .distinct()
            .collect(HashMap::new, (m, v) -> m.put(v, context.getVariable(v)), HashMap::putAll);
        try {
            return recursive
                ? VariableRenderer.renderRecursively(1, template, variables)
                : VariableRenderer.renderOnce(template, variables);
        } catch (IllegalVariableEvaluationException e) {
            throw new PebbleException(e, e.getMessage());
        }
    }
}
