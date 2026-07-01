package io.kestra.core.preview;

import io.kestra.core.models.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

@io.kestra.core.models.annotations.Plugin
public interface FileRenderer extends Plugin {
    boolean supports(String extension);

    FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException;
}
