package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Singleton
public class IsFileEmptyFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'isFileEmpty' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) throws IOException {
        return switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME -> {
                try (InputStream inputStream = storageInterface.get(tenantId, namespace, path)) {
                    byte[] buffer = new byte[1];
                    yield inputStream.read(buffer, 0, 1) <= 0;
                }
            }
            case LocalPath.FILE_SCHEME -> {
                try (InputStream inputStream = localPathFactory.createLocalPath().get(path)) {
                    byte[] buffer = new byte[1];
                    yield inputStream.read(buffer, 0, 1) <= 0;
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