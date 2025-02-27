package io.kestra.core.runners.pebble.functions;

import io.kestra.core.utils.Slugify;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.net.URI;
import java.util.Map;

abstract class AbstractFileFunction implements Function {
    static final String KESTRA_SCHEME = "kestra:///";
    static final String TRIGGER = "trigger";
    static final String NAMESPACE = "namespace";
    static final String ID  = "id";

    URI getUriFromThePath(Object path, int lineNumber, PebbleTemplate self) {
        if (path instanceof URI u) {
            return u;
        } else if (path instanceof String str && str.startsWith(KESTRA_SCHEME)) {
            return URI.create(str);
        } else {
            throw new PebbleException(null, "Unable to create the URI from the path " + path, lineNumber, self.getName());
        }
    }

    boolean isFileUriValid(String namespace, String flowId, String executionId, URI path) {
        // Internal storage URI should be: kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion or kestra:///$namespace/$flowId/executions/$executionId/trigger/$triggerName/$random.ion
        // We check that the file is for the given flow execution
        if (namespace == null || flowId == null || executionId == null) {
            return false;
        }

        String authorizedBasePath = KESTRA_SCHEME + namespace.replace(".", "/") + "/" + Slugify.of(flowId) + "/executions/" + executionId + "/";
        return path.toString().startsWith(authorizedBasePath);
    }

    @SuppressWarnings("unchecked")
    void checkAllowedFile(EvaluationContext context, URI path) {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");

        // check if the file is from the current execution or the parent execution
        boolean isFileFromCurrentExecution = isFileUriValid(flow.get(NAMESPACE), flow.get(ID), execution.get(ID), path);
        if (!isFileFromCurrentExecution) {
            checkIfFileFromParentExecution(context, path);
        }
    }

    @SuppressWarnings("unchecked")
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
}
