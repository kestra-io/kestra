package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class FileRenderBuilder {
    private static final Charset DEFAULT_FILE_CHARSET = StandardCharsets.UTF_8;

    public static FileRender of(String extension, InputStream filestream, Optional<Charset> charset, Integer maxLine) throws IOException {
        if (ImageFileRender.ImageFileExtension.isImageFileExtension(extension)) {
            return new ImageFileRender(extension, filestream, maxLine);
        }

        return switch (extension.toLowerCase()) {
            case "ion" -> new IonFileRender(extension, filestream, maxLine);
            case "md" -> new DefaultFileRender(extension, filestream, DEFAULT_FILE_CHARSET, FileRender.Type.MARKDOWN, maxLine);
            case "pdf" -> new PdfFileRender(extension, filestream, maxLine);
            default -> new DefaultFileRender(extension, filestream, charset.orElse(DEFAULT_FILE_CHARSET), maxLine);
        };
    }
}
