package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Singleton
public class FileSizeFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'fileSize' function expects an argument 'path' that is a path to the internal storage URI.";

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

    @SuppressWarnings("unchecked")
    private long getFileSizeFromInternalStorageUri(EvaluationContext context, URI path) throws IOException {
        // check if the file is from the current execution or the parent execution
        checkAllowedFile(context, path);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        FileAttributes fileAttributes = storageInterface.getAttributes(flow.get("tenantId"), flow.get("namespace"), path);
        return fileAttributes.getSize();
    }
}
