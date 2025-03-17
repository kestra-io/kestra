package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Singleton
public class IsFileEmptyFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'isFileEmpty' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @SuppressWarnings("unchecked")
    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) throws IOException {
        try (InputStream inputStream = storageInterface.get(tenantId, namespace, path)) {
            byte[] buffer = new byte[1];
            return inputStream.read(buffer, 0, 1) <= 0;
        }
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}