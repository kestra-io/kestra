package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

@Singleton
public class IsFileEmptyFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'isFileEmpty' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
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
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                FileAttributes fileAttributes = namespaceFactory
                    .of(tenantId, namespace, storageInterface)
                    .getFileMetadata(NamespaceFile.normalize(Path.of(path.getPath()), true));
                yield fileAttributes.getSize() <= 0;
            }
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
