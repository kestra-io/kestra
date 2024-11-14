package io.kestra.core.runners.pebble.functions;

import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class GetFileURI implements Function {
    private static final String ERROR_MESSAGE = "The 'getFileURI' function expects an argument 'path' that is a path to a namespace file.";

    @Override
    public List<String> getArgumentNames() {
        return List.of("path");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("path")) {
            throw new PebbleException(null, ERROR_MESSAGE, lineNumber, self.getName());
        }

        Object path = args.get("path");
        @SuppressWarnings("unchecked")
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        return StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(flow.get("namespace")) + "/" + path;
    }
}
