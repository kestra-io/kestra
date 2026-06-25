package io.kestra.core.runners.pebble.functions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

@Singleton
public class FileURIFunction extends AbstractFileFunction {
    public static final String NAME = "fileURI";
    public static final String REVISION = "revision";

    private static final String ERROR_MESSAGE = "The 'fileURI' function expects an argument 'path' that is a path to a namespace file.";

    @Override
    public List<String> getArgumentNames() {
        return Stream.concat(
            super.getArgumentNames().stream(),
            Stream.of(REVISION)
        ).toList();
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put(PATH, "'a/namespace/file'");
        defaults.put(NAMESPACE, null);
        defaults.put(REVISION, null);
        return defaults;
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
        Namespace namespaceStorage = namespaceFactory.get().of(tenantId, namespace, storageInterface.get());
        Path filePath = NamespaceFile.normalize(Path.of(pathStr));

        if (args.containsKey(REVISION)) {
            Integer revision;
            try {
                revision = Integer.parseInt(args.get(REVISION).toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The 'fileURI' function expects the 'revision' argument to be a valid integer.");
            }
            try {
                namespaceStorage.getFileContent(filePath, revision).close();
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Revision " + revision + " of file '" + filePath + "' was not found in namespace '" + namespace + "'.");
            }
            NamespaceFile namespaceFile = NamespaceFile.of(namespace, filePath, revision);
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
