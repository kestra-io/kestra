package io.kestra.core.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.models.Plugin;

@io.kestra.core.models.annotations.Plugin
public interface FileRenderer extends Plugin {
    boolean supports(String extension);

    /**
     * Returns the file extensions this renderer handles, lowercase and without a leading dot.
     * Declared statically so the plugin schema bundle can advertise which artifact provides a
     * renderer for an extension, and the preview can fetch it on demand; {@link #supports(String)}
     * remains the runtime check.
     *
     * @return the supported extensions, empty when the renderer does not declare them.
     */
    default Set<String> extensions() {
        return Set.of();
    }

    FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException;
}
