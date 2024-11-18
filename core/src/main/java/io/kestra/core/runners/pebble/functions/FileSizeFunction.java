package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Slugify;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.pebbletemplates.pebble.extension.Function;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Singleton
public class FileSizeFunction implements Function {
    private static final String ERROR_MESSAGE = "The 'fileSize' function expects an argument 'path' that is a path to the internal storage URI.";
    private static final String KESTRA_SCHEME = "kestra:///";
    private static final String TRIGGER = "trigger";
    private static final String NAMESPACE = "namespace";
    private static final String ID  = "id";

    @Inject
    private StorageInterface storageInterface;

    @Override
    public List<String> getArgumentNames() {
        return List.of("path");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {

        if (!args.containsKey("path")) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        Object path = args.get("path");
        URI uri = getUriFromThePath(path, lineNumber, self);

        try {
            return getFileSizeFromInternalStorageUri(context, uri);
        } catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }

    }

    private URI getUriFromThePath(Object path, int lineNumber, PebbleTemplate self) {
        if (path instanceof URI u) {
            return u;
        } else if (path instanceof String str && str.startsWith(KESTRA_SCHEME)) {
            return URI.create(str);
        } else {
            throw new PebbleException(null, "Unable to create the URI from the path " + path, lineNumber, self.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private long getFileSizeFromInternalStorageUri(EvaluationContext context, URI path) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");

        boolean isFileFromCurrentExecution = isFileUriValid(flow.get(NAMESPACE), flow.get(ID), execution.get(ID), path);

        if (!isFileFromCurrentExecution) {
            checkIfFileFromParentExecution(context, path);
        }

        FileAttributes fileAttributes = storageInterface.getAttributes(flow.get("tenantId"), path);
        return fileAttributes.getSize();
    }

    private void checkIfFileFromParentExecution(EvaluationContext context, URI path) {
        if (context.getVariable(TRIGGER) != null) {
            // if there is a trigger of type execution, we also allow accessing a file from the parent execution
            Map<String, String> trigger = (Map<String, String>) context.getVariable(TRIGGER);

            if (!isFileUriValid(trigger.get(NAMESPACE), trigger.get("flowId"), trigger.get("executionId"), path)) {
                throw new IllegalArgumentException("Unable to read the file '" + path + "' as it didn't belong to the current execution");
            }
        }
        else {
            throw new IllegalArgumentException("Unable to read the file '" + path + "' as it didn't belong to the current execution");
        }
    }

    private boolean isFileUriValid(String namespace, String flowId, String executionId, URI path) {
        // Internal storage URI should be: kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion or kestra:///$namespace/$flowId/executions/$executionId/trigger/$triggerName/$random.ion
        // We check that the file is for the given flow execution
        if (namespace == null || flowId == null || executionId == null) {
            return false;
        }

        String authorizedBasePath = KESTRA_SCHEME + namespace.replace(".", "/") + "/" + Slugify.of(flowId) + "/executions/" + executionId + "/";
        return path.toString().startsWith(authorizedBasePath);
    }
}
