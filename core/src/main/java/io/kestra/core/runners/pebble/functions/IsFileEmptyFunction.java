package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.StorageInterface;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Singleton
public class IsFileEmptyFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'isFileEmpty' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

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
            return readAndCheckEmptyFileFromInternalStorage(context, uri);
        } catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }

    }

    @SuppressWarnings("unchecked")
    private boolean readAndCheckEmptyFileFromInternalStorage(EvaluationContext context, URI path) throws IOException {
        // check if the file is from the current execution, the parent execution, or an allowed namespace
        String namespace = checkAllowedFileAndReturnNamespace(context, path);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        try (InputStream inputStream = storageInterface.get(flow.get(TENANT_ID), namespace, path)) {
            byte[] buffer = new byte[1];
            return inputStream.read(buffer, 0, 1) <= 0;
        }
    }
}