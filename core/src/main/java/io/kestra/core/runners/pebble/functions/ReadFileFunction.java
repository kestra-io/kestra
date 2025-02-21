package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.annotation.Value;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Singleton
public class ReadFileFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @Inject
    private StorageInterface storageInterface;

    @Value("${kestra.server-type:}") // default to empty as tests didn't set this property
    private String serverType;

    @Override
    public List<String> getArgumentNames() {
        return List.of("path");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        // TODO it will be enabled on the next release so the code is kept commented out
        //  don't forget to also re-enabled the test
//        if (!calledOnWorker()) {
//            throw new PebbleException(null, "The 'read' function can only be used in the Worker as it access the internal storage.", lineNumber, self.getName());
//        }

        if (!args.containsKey("path")) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        Object path = args.get("path");
        if (path instanceof URI uri) {
            try {
                return readFromInternalStorageUri(context, uri);
            }
            catch (IOException e) {
                throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
            }

        } else if (path instanceof String str){
            try {
                return str.startsWith(KESTRA_SCHEME) ? readFromInternalStorageUri(context, URI.create(str)) : readFromNamespaceFile(context, str);
            }
            catch (IOException e) {
                throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
            }
        } else {
            throw new PebbleException(null, "Unable to read the file " + path, lineNumber, self.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private String readFromNamespaceFile(EvaluationContext context, String path) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        URI namespaceFile = URI.create(StorageContext.namespaceFilePrefix(flow.get("namespace")) + "/" + path);
        try (InputStream inputStream = storageInterface.get(flow.get("tenantId"), flow.get("namespace"), namespaceFile)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private String readFromInternalStorageUri(EvaluationContext context, URI path) throws IOException {
        // check if the file is from the current execution or the parent execution
        checkAllowedFile(context, path);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        try (InputStream inputStream = storageInterface.get(flow.get("tenantId"), flow.get("namespace"), path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}