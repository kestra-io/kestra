package io.kestra.core.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

import io.kestra.core.models.Plugin;

@io.kestra.core.models.annotations.Plugin
public interface FileRenderer extends Plugin {
    boolean supports(String extension);

    FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException;
}
