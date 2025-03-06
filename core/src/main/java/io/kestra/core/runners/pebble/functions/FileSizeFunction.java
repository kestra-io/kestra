package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.FileAttributes;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Singleton
public class FileSizeFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'fileSize' function expects an argument 'path' that is a path to the internal storage URI.";

    @SuppressWarnings("unchecked")
    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        FileAttributes fileAttributes = storageInterface.getAttributes(flow.get(TENANT_ID), namespace, path);
        return fileAttributes.getSize();
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
