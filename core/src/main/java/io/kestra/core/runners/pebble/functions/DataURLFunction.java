package io.kestra.core.runners.pebble.functions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.MediaType;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

@Singleton
public class DataURLFunction extends AbstractFileFunction {
    public static final String NAME = "dataURL";
    public static final String MIME_TYPE = "mimeType";
    public static final String CONTENT_ONLY = "contentOnly";

    private static final String ERROR_MESSAGE = "The 'dataURL' function expects an argument 'path' that is a path to a namespace file or an internal storage URL.";

    @Override
    public List<String> getArgumentNames() {
        return Stream.concat(
            super.getArgumentNames().stream(),
            Stream.of(REVISION, MIME_TYPE, CONTENT_ONLY)
        ).toList();
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put(PATH, "'a/namespace/file'");
        defaults.put(NAMESPACE, "flow.namespace");
        defaults.put(REVISION, null);
        defaults.put(MIME_TYPE, null);
        defaults.put(CONTENT_ONLY, null);
        return defaults;
    }

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId, Map<String, Object> args) throws IOException {
        boolean contentOnly = Boolean.TRUE.equals(args.get(CONTENT_ONLY));
        String mimeType = contentOnly ? null : resolveMimeType(path, args);

        byte[] bytes;
        try (InputStream inputStream = openStream(NAME, path, namespace, tenantId, args)) {
            bytes = inputStream.readAllBytes();
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);

        return contentOnly ? base64 : "data:" + mimeType + ";base64," + base64;
    }

    private String resolveMimeType(URI path, Map<String, Object> args) {
        if (args.containsKey(MIME_TYPE)) {
            return (String) args.get(MIME_TYPE);
        }

        return MediaType.forExtension(NameUtils.extension(path.toString()).toLowerCase(Locale.ENGLISH))
            .map(MediaType::toString)
            .orElseThrow(() -> new IllegalArgumentException("Cannot process the URL %s: unrecognized file extension.".formatted(path)));
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
