package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;

public class FileRenderBuilder {
    public static FileRender of(String extension, InputStream filestream, Integer maxLine) throws IOException {
        if (ImageFileRender.ImageFileExtension.isImageFileExtension(extension)) {
            return new ImageFileRender(extension, filestream, maxLine);
        }

        return switch (extension) {
            case "ion" -> new IonFileRender(extension, filestream, maxLine);
            case "md" -> new DefaultFileRender(extension, filestream, FileRender.Type.MARKDOWN, maxLine);
            default -> new DefaultFileRender(extension, filestream, maxLine);
        };
    }
}
