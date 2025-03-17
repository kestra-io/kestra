package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.RunVariables;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.FlowService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class SecretFunction implements Function {
    @Inject
    private SecretService secretService;

    @Inject
    private FlowService flowService;

    @Override
    public List<String> getArgumentNames() {
        return List.of("key", "namespace");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String key = getSecretKey(args, self, lineNumber);
        String namespace = (String) args.get("namespace");

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String flowNamespace = flow.get("namespace");
        String flowTenantId = flow.get("tenantId");

        if (namespace == null) {
            namespace = flowNamespace;
        } else {
            flowService.checkAllowedNamespace(flowTenantId, namespace, flowTenantId, flowNamespace);
        }

        try {
            String secret = secretService.findSecret(flowTenantId, namespace, key);

            try {
                Consumer<String> addSecretConsumer = (Consumer<String>) context.getVariable(RunVariables.SECRET_CONSUMER_VARIABLE_NAME);
                addSecretConsumer.accept(secret);
            } catch (Exception e) {
                log.warn("Unable to get secret consumer", e);
            }

            return secret;
        } catch (SecretNotFoundException | IOException e) {
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