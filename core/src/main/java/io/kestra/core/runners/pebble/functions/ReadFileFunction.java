package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Singleton
public class ReadFileFunction implements Function {
    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' with structure {filePath}.";

    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;

    @Override
    public List<String> getArgumentNames() {
        return List.of("path");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");

        try {
            return new String(storageInterface.get(tenantService.resolveTenant(), getStorageUri(flow.get("namespace"), args, self, lineNumber)).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    protected URI getStorageUri(String namespace, Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey("path")) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        String path = (String) args.get("path");
        return URI.create(storageInterface.namespaceFilePrefix(namespace) + "/" + path);
    }
}