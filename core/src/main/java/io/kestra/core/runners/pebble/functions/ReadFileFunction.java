package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Singleton
public class ReadFileFunction implements Function {
    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";
    private static final String KESTRA_SCHEME = "kestra:///";

    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;

    @Override
    public List<String> getArgumentNames() {
        return List.of("path");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("path")) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        String path = (String) args.get("path");
        try {
            return path.startsWith(KESTRA_SCHEME) ? readFromInternalStorageUri(context, path) : readFromNamespaceFile(context, path);
        }
        catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    private String readFromNamespaceFile(EvaluationContext context, String path) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        URI namespaceFile = URI.create(storageInterface.namespaceFilePrefix(flow.get("namespace")) + "/" + path);
        return new String(storageInterface.get(tenantService.resolveTenant(), namespaceFile).readAllBytes(), StandardCharsets.UTF_8);
    }

    private String readFromInternalStorageUri(EvaluationContext context, String path) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");

        // check if the file is from the current execution
        if (!validateFileUri(flow.get("namespace"), flow.get("id"), execution.get("id"), path)) {
            // if not, it can be from the parent execution, so we check if there is a trigger of type execution
            if (context.getVariable("trigger") != null) {
                // if there is a trigger of type execution, we also allow accessing a file from the parent execution
                Map<String, String> trigger = (Map<String, String>) context.getVariable("trigger");
                if (!validateFileUri(trigger.get("namespace"), trigger.get("flowId"), trigger.get("executionId"), path)) {
                    throw new IllegalArgumentException("Unable to read a file that didn't belong to the current execution");
                }
            }
            else {
                throw new IllegalArgumentException("Unable to read a file that didn't belong to the current execution");
            }
        }
        URI internalStorageFile = URI.create(path);
        return new String(storageInterface.get(tenantService.resolveTenant(), internalStorageFile).readAllBytes(), StandardCharsets.UTF_8);
    }

    private boolean validateFileUri(String namespace, String flowId, String executionId, String path) {
        // Internal storage URI should be: kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion or kestra:///$namespace/$flowId/executions/$executionId/trigger/$triggerName/$random.ion
        // We check that the file is for the given flow execution
        String authorizedBasePath = KESTRA_SCHEME + namespace + "/" + flowId + "/executions/" + executionId + "/";
        return path.startsWith(authorizedBasePath);
    }
}