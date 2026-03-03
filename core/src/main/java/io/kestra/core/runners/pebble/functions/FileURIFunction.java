package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Singleton
public class FileURIFunction extends AbstractFileFunction {
    public static final String VERSION = "version";

    private static final String ERROR_MESSAGE = "The 'fileURI' function expects an argument 'path' that is a path to a namespace file.";

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
            case StorageContext.KESTRA_SCHEME -> path.toString();
            case LocalPath.FILE_SCHEME -> path.toString();
            case Namespace.NAMESPACE_FILE_SCHEME -> getNamespaceFileURI(path, namespace, tenantId, args);
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    private String getNamespaceFileURI(URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
        String pathStr = path.getPath();
        if (pathStr.contains("../")) {
            throw new IllegalArgumentException("Path must not contain '../'");
        }
        Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
       Path filePath = NamespaceFile.normalize(Path.of(pathStr));

        if (args.containsKey(VERSION)) {
            Integer version;
            try {
                version = Integer.parseInt(args.get(VERSION).toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The 'fileURI' function expects the 'version' argument to be a valid integer.");
            }
            try {
                namespaceStorage.getFileContent(filePath, version).close();
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Version " + version + " of file '" + filePath + "' was not found in namespace '" + namespace + "'.");
            }
            NamespaceFile namespaceFile = NamespaceFile.of(namespace, filePath, version);
            return namespaceFile.uri().toString();
        } else {
            NamespaceFile namespaceFile = namespaceStorage.get(filePath);
            return namespaceFile.uri().toString();
        }
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
