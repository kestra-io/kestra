package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Singleton
public class ReadFileFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @SuppressWarnings("unchecked")
    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace) throws IOException {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        try (InputStream inputStream = storageInterface.get(flow.get(TENANT_ID), namespace, path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}