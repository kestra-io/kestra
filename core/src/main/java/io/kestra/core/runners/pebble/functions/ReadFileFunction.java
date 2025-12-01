package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Singleton
public class ReadFileFunction extends AbstractFileFunction {
    public static final String VERSION = "version";

    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

    @Override
    public List<String> getArgumentNames() {
        return Stream.concat(
            super.getArgumentNames().stream(),
            Stream.of(VERSION)
        ).toList();
    }

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
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
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                try (InputStream inputStream = contentInputStream(path, namespace, tenantId, args)) {
                    yield new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    private InputStream contentInputStream(URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
        Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);

        if (args.containsKey(VERSION)) {
            return namespaceStorage.getFileContent(
                    NamespaceFile.normalize(Path.of(path.getPath()), true),
                    Integer.parseInt(args.get(VERSION).toString())
                );
        }

        return namespaceStorage.getFileContent(NamespaceFile.normalize(Path.of(path.getPath()), true));
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
