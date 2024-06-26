package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.utils.TruthUtils;
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
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class KvFunction implements Function {
    @Inject
    private FlowService flowService;

    @Inject
    private StorageInterface storageInterface;

    @Override
    public List<String> getArgumentNames() {
        return List.of("key", "namespace", "errorOnMissing");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String key = getKey(args, self, lineNumber);
        String namespace = (String) args.get("namespace");
        Boolean errorOnMissing = (Boolean) args.get("errorOnMissing");

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String flowNamespace = flow.get("namespace");
        String flowTenantId = flow.get("tenantId");
        if (namespace == null) {
            namespace = flowNamespace;
        } else {
            flowService.checkAllowedNamespace(flowTenantId, namespace, flowTenantId, flowNamespace);
        }

        Optional<Object> value;
        try {
            value = new InternalKVStore(flowTenantId, namespace, storageInterface).get(key);
        } catch (Exception e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }

        if (value.isEmpty() && errorOnMissing == Boolean.TRUE) {
            throw new PebbleException(null, "The key '" + key + "' does not exist in the namespace '" + namespace + "'.", lineNumber, self.getName());
        }

        return value.orElse(null);
    }

    protected String getKey(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey("key")) {
            throw new PebbleException(null, "The 'kv' function expects an argument 'key'.", lineNumber, self.getName());
        }

        return (String) args.get("key");
    }
}