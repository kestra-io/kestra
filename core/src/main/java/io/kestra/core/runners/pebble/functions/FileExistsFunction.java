package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.net.URI;

@Singleton
public class FileExistsFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'fileExists' function expects an argument 'path' that is a path to the internal storage URI.";

    @SuppressWarnings("unchecked")
    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) {
        return storageInterface.exists(tenantId, namespace, path);
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
