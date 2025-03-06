package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

@Singleton
public class IsFileEmptyFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'isFileEmpty' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @SuppressWarnings("unchecked")
    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        try (InputStream inputStream = storageInterface.get(flow.get(TENANT_ID), namespace, path)) {
            byte[] buffer = new byte[1];
            return inputStream.read(buffer, 0, 1) <= 0;
        }
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}