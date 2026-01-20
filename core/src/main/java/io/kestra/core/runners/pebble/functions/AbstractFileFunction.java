package io.kestra.core.runners.pebble.functions;

import com.cronutils.utils.VisibleForTesting;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.LocalPathFactory;
import io.kestra.core.services.NamespaceService;
import io.kestra.core.storages.*;
import io.kestra.core.utils.Slugify;
import io.micronaut.context.annotation.Value;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

abstract class AbstractFileFunction implements Function {
    static final String SCHEME_NOT_SUPPORTED_ERROR = "Cannot process the URI %s: scheme not supported.";
    static final String KESTRA_SCHEME = "kestra:///";
    static final String TRIGGER = "trigger";
    static final String NAMESPACE = "namespace";
    static final String TENANT_ID = "tenantId";
    static final String ID  = "id";
    static final String PATH = "path";

    private static final Pattern URI_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final Pattern EXECUTION_FILE = Pattern.compile(".*/.*/executions/.*/tasks/.*/.*");

    @Inject
    protected NamespaceService namespaceService;

    @Inject
    protected StorageInterface storageInterface;

    @Inject
    protected LocalPathFactory localPathFactory;

    @Inject
    protected NamespaceFactory namespaceFactory;

    @Value("${" + LocalPath.ENABLE_FILE_FUNCTIONS_CONFIG + ":true}")
    protected boolean enableFileProtocol;

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
                } else if (str.startsWith(LocalPath.FILE_PROTOCOL)) {
                    fileUri = URI.create(str);
                    namespace = checkEnabledLocalFileAndReturnNamespace(args, flow);
                } else if (str.startsWith(Namespace.NAMESPACE_FILE_SCHEME)) {
                    fileUri = URI.create(str);
                    namespace = checkedAllowedNamespaceAndReturnNamespace(args, fileUri, tenantId, flow);
                } else if (URI_PATTERN.matcher(str).matches()) {
                    // it is an unsupported URI
                    throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(str));
                } else {
                    fileUri = URI.create(Namespace.NAMESPACE_FILE_SCHEME + ":///" + str);
                    namespace = (String) Optional.ofNullable(args.get(NAMESPACE)).orElse(flow.get(NAMESPACE));
                    namespaceService.checkAllowedNamespace(tenantId, namespace, tenantId, flow.get(NAMESPACE));
                }
            } else {
                throw new PebbleException(null, "Unable to read the file " + path, lineNumber, self.getName());
            }
            return fileFunction(context, fileUri, namespace, tenantId, args);
        } catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of(PATH, NAMESPACE);
    }

    protected abstract String getErrorMessage();

    protected abstract Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException;

    boolean isFileUriValid(String namespace, String flowId, String executionId, URI path) {
        // Internal storage URI should be: kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion or kestra:///$namespace/$flowId/executions/$executionId/trigger/$triggerName/$random.ion
        // Namespace file URI should be: kestra:///$namespace/_files/$random'
        // We check that the file is for the given flow execution
        if (namespace == null || flowId == null || executionId == null) {
            return false;
        }

        String executionAuthorizedBasePath = KESTRA_SCHEME + namespace.replace(".", "/") + "/" + Slugify.of(flowId) + "/executions/" + executionId + "/";
        String nsFileAuthorizedBasePath = KESTRA_SCHEME + namespace.replace(".", "/") + "/_files/"  ;
        return path.toString().startsWith(executionAuthorizedBasePath) || path.toString().startsWith(nsFileAuthorizedBasePath);
    }

    @SuppressWarnings("unchecked")
    private String checkAllowedFileAndReturnNamespace(EvaluationContext context, URI path) {
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

            return isFileUriValid(trigger.get(NAMESPACE), trigger.get("flowId"), trigger.get("executionId"), path);
        }
        return false;
    }

    private String checkIfFileFromAllowedNamespaceAndReturnIt(URI path, String tenantId, String fromNamespace) {

        String namespace = extractNamespace(path);
        namespaceService.checkAllowedNamespace(tenantId, namespace, tenantId, fromNamespace);
        return namespace;
    }
    private String checkEnabledLocalFileAndReturnNamespace(Map<String, Object> args, Map<String, String> flow) {
        if (!enableFileProtocol) {
            throw new SecurityException("The file:// protocol has been disabled inside the Kestra configuration.");
        }

        return (String) Optional.ofNullable(args.get(NAMESPACE)).orElse(flow.get(NAMESPACE));
    }

    private String checkedAllowedNamespaceAndReturnNamespace(Map<String, Object> args, URI nsFileUri, String tenantId, Map<String, String> flow) {
        if (args.get(NAMESPACE) != null && nsFileUri.getAuthority() != null) {
            throw new IllegalArgumentException("You cannot set a namespace both as the function argument and inside the URI");
        }

        // we will transform nsfile URI into a kestra URI so it is handled seamlessly by all functions
        String customNs = Optional.ofNullable((String) args.get(NAMESPACE)).orElse(nsFileUri.getAuthority());
        if (customNs != null) {
            namespaceService.checkAllowedNamespace(tenantId, customNs, tenantId, flow.get(NAMESPACE));
        }
        return Optional.ofNullable(customNs).orElse(flow.get(NAMESPACE));
    }

    @VisibleForTesting
    String extractNamespace( URI path){
        // Extract namespace from the path, it should be of the form: kestra:///{namespace}/{flowId}/executions/{executionId}/tasks/{taskId}/{taskRunId}/{fileName}'
        // To extract the namespace, we must do it step by step as namespace and taskId can contain the words 'executions' and 'tasks'
        String namespace = path.toString().substring(KESTRA_SCHEME.length());
        if (!EXECUTION_FILE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Unable to read the file '" + path + "' as it is not an execution file");
        }
        // 1. remove everything after tasks
        namespace = namespace.substring(0, namespace.lastIndexOf("/tasks/"));
        // 2. remove everything after executions
        namespace = namespace.substring(0, namespace.lastIndexOf("/executions/"));
        // 3. remove the flowId
        namespace = namespace.substring(0, namespace.lastIndexOf('/'));
        // 4. replace '/' with '.'
        namespace = namespace.replace("/", ".");

        return namespace;
    }
}
