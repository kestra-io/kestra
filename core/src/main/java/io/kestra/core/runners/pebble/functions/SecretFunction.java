package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.secret.SecretService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Singleton
public class SecretFunction implements Function {
    @Inject
    private SecretService secretService;

    @Override
    public List<String> getArgumentNames() {
        return List.of("key");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String key = getSecretKey(args, self, lineNumber);
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");

        try {
            return secretService.findSecret(flow.get("namespace"), key);
        } catch (IllegalVariableEvaluationException | IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    protected String getSecretKey(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey("key")) {
            throw new PebbleException(null, "The 'secret' function expects an argument 'key'.", lineNumber, self.getName());
        }

        return (String) args.get("key");
    }
}