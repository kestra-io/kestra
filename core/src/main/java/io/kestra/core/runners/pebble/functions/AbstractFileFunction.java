package io.kestra.core.runners.pebble.functions;

import io.kestra.core.services.FlowService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Slugify;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

abstract class AbstractFileFunction implements Function {

    static final String KESTRA_SCHEME = "kestra:///";
    static final String TRIGGER = "trigger";
    static final String NAMESPACE = "namespace";
    static final String TENANT_ID = "tenantId";
    static final String ID  = "id";
    static final String PATH = "path";

    private static final Pattern EXECUTION_FILE = Pattern.compile(".*/.*/executions/.*/tasks/.*/.*");

    @Inject
    protected FlowService flowService;

    @Inject
    protected StorageInterface storageInterface;

    //    @Value("${kestra.server-type:}") // default to empty as tests didn't set this property
//    private String serverType;

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        // TODO it will be enabled on the next release so the code is kept commented out
        //  don't forget to also re-enabled the test
//        if (!calledOnWorker()) {
//            throw new PebbleException(null, "The 'read' function can only be used in the Worker as it access the internal storage.", lineNumber, self.getName());
//        }

        if (!args.containsKey(PATH)) {
            throw new PebbleException(null, getErrorMessage(), lineNumber, self.getName());
        }

        Object path = args.get(PATH);

        try {
            URI fileUri;
            String namespace;
            Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
            String tenantId = flow.get(TENANT_ID);

            if (path instanceof URI uri) {
                fileUri = uri;
                namespace = checkAllowedFileAndReturnNamespace(context, fileUri);
            } else if (path instanceof String str) {
                if (str.startsWith(KESTRA_SCHEME)) {
                    fileUri = URI.create(str);
                    namespace = checkAllowedFileAndReturnNamespace(context, fileUri);
                } else {
                    namespace = (String) Optional.ofNullable(args.get(NAMESPACE)).orElse(flow.get(NAMESPACE));
                    fileUri = URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + str);
                    flowService.checkAllowedNamespace(tenantId, namespace, tenantId, flow.get(NAMESPACE));
                }
            } else {
                throw new PebbleException(null, "Unable to read the file " + path, lineNumber, self.getName());
            }
            return fileFunction(context, fileUri, namespace, tenantId);
        } catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of(PATH, NAMESPACE);
    }

    protected abstract String getErrorMessage();

    protected abstract Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) throws IOException;

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
    String checkAllowedFileAndReturnNamespace(EvaluationContext context, URI path) {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");

        // check if the file is from the current execution, the parent execution or an allowed namespaces
        boolean isFileFromCurrentExecution = isFileUriValid(flow.get(NAMESPACE), flow.get(ID), execution.get(ID), path);
        if (isFileFromCurrentExecution) {
            return flow.get(NAMESPACE);
        } else {
            if (isFileFromParentExecution(context, path)) {
                Map<String, String> trigger = (Map<String, String>) context.getVariable(TRIGGER);
                return trigger.get(NAMESPACE);
            }
            else {
                return checkIfFileFromAllowedNamespaceAndReturnIt(path, flow.get(TENANT_ID), flow.get(NAMESPACE));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isFileFromParentExecution(EvaluationContext context, URI path) {
        if (context.getVariable(TRIGGER) != null) {
            // if there is a trigger of type execution, we also allow accessing a file from the parent execution
            Map<String, String> trigger = (Map<String, String>) context.getVariable(TRIGGER);

            if (!isFileUriValid(trigger.get(NAMESPACE), trigger.get("flowId"), trigger.get("executionId"), path)) {
                throw new IllegalArgumentException("Unable to read the file '" + path + "' as it didn't belong to the parent execution");
            }
            return true;
        }
        return false;
    }

    private String checkIfFileFromAllowedNamespaceAndReturnIt(URI path, String tenantId, String fromNamespace) {
        // Extract namespace from the path, it should be of the form: kestra:///({tenantId}/){namespace}/{flowId}/executions/{executionId}/tasks/{taskId}/{taskRunId}/{fileName}'
        // To extract the namespace, we must do it step by step as tenantId, namespace and taskId can contain the words 'executions' and 'tasks'
        String namespace = path.toString().substring(KESTRA_SCHEME.length());
        if (!EXECUTION_FILE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Unable to read the file '" + path + "' as it is not an execution file");
        }

        // 1. remove the tenantId if existing
        if (tenantId != null) {
            namespace = namespace.substring(tenantId.length() + 1);
        }
        // 2. remove everything after tasks
        namespace = namespace.substring(0, namespace.lastIndexOf("/tasks/"));
        // 3. remove everything after executions
        namespace = namespace.substring(0, namespace.lastIndexOf("/executions/"));
        // 4. remove the flowId
        namespace = namespace.substring(0, namespace.lastIndexOf('/'));
        // 5. replace '/' with '.'
        namespace = namespace.replace("/", ".");

        flowService.checkAllowedNamespace(tenantId, namespace, tenantId, fromNamespace);

        return namespace;
    }
}
