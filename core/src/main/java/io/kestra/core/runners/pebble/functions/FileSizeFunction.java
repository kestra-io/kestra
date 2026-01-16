package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

@Singleton
public class FileSizeFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'fileSize' function expects an argument 'path' that is a path to the internal storage URI.";

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
        return switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME -> {
                FileAttributes fileAttributes = storageInterface.getAttributes(tenantId, namespace, path);
                yield fileAttributes.getSize();
            }
            case LocalPath.FILE_SCHEME -> {
                BasicFileAttributes fileAttributes = localPathFactory.createLocalPath().getAttributes(path);
                yield fileAttributes.size();
            }
            case Namespace.NAMESPACE_FILE_SCHEME  -> {
                FileAttributes fileAttributes = namespaceFactory
                    .of(tenantId, namespace, storageInterface)
                    .getFileMetadata(NamespaceFile.normalize(Path.of(path.getPath()), true));
                yield fileAttributes.getSize();
            }
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
