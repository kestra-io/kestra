package io.kestra.core.runners.pebble.functions;

import io.kestra.core.services.NamespaceService;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
public class FileURIFunction implements Function {
    private static final String ERROR_MESSAGE = "The 'fileURI' function expects an argument 'path' that is a path to a namespace file.";
    public static final String VERSION = "version";
    private static final String PATH = "path";
    private static final String NAMESPACE = "namespace";
    private static final String TENANT_ID = "tenantId";

    @Inject
    protected NamespaceService namespaceService;

    @Inject
    protected NamespaceFactory namespaceFactory;

    @Inject
    protected StorageInterface storageInterface;

    @Override
    public List<String> getArgumentNames() {
        return Stream.concat(
            Stream.of(PATH, NAMESPACE),
            Stream.of(VERSION)
        ).toList();
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey(PATH)) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        String path = (String) args.get(PATH);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.contains("../")) {
            throw new IllegalArgumentException("Path must not contain '../'");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String namespace = (String) Optional.ofNullable(args.get(NAMESPACE)).orElse(flow.get(NAMESPACE));
        String tenantId = flow.get(TENANT_ID);

        namespaceService.checkAllowedNamespace(tenantId, namespace, tenantId, flow.get(NAMESPACE));

        try {
            Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
            Path filePath = NamespaceFile.normalize(Path.of(path), true);

            if (args.containsKey(VERSION)) {
                Integer version;
                try {
                    version = Integer.parseInt(args.get(VERSION).toString());
                } catch (NumberFormatException e) {
                    throw new PebbleException(null, "The 'fileURI' function expects the 'version' argument to be a valid integer.", lineNumber, self.getName());
                }
                try {
                    namespaceStorage.getFileContent(filePath, version).close();
                } catch (java.io.FileNotFoundException e) {
                    throw new PebbleException(null, "Version " + version + " of file '" + filePath + "' was not found in namespace '" + namespace + "'.", lineNumber, self.getName());
                }
                NamespaceFile namespaceFile = NamespaceFile.of(namespace, filePath, version);
                return namespaceFile.uri().toString();
            } else {
                NamespaceFile namespaceFile = namespaceStorage.get(filePath);
                return namespaceFile.uri().toString();
            }
        } catch (IOException e) {
            return StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + "/" + path;
        }
    }
}
