package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Singleton
public class ReadFileFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) throws IOException {
        return switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME -> {
                try (InputStream inputStream = storageInterface.get(tenantId, namespace, path)) {
                    yield new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            case LocalPath.FILE_SCHEME -> {
                try (InputStream inputStream = localPathFactory.createLocalPath().get(path)) {
                    yield new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}