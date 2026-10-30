package io.kestra.core.runners.pebble.functions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.amazon.ion.util.IonStreamUtils;

import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

@Singleton
public class ReadFileFunction extends AbstractFileFunction {
    public static final String NAME = "read";

    private static final String ERROR_MESSAGE = "The 'read' function expects an argument 'path' that is a path to a namespace file or an internal storage URI.";

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
        defaults.put(NAMESPACE, "flow.namespace");
        defaults.put(REVISION, null);
        return defaults;
    }

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
        try (InputStream inputStream = openStream(NAME, path, namespace, tenantId, args)) {
            return readContent(inputStream);
        }
    }

    // Returns byte[] for binary ION (preserves fidelity for fromIon()), String for everything else
    private static Object readContent(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        if (IonStreamUtils.isIonBinary(bytes)) {
            return bytes;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
