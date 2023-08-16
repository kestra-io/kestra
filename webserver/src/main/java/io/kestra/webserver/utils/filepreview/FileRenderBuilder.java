package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;

public class FileRenderBuilder {

    public static FileRender build(String extension, InputStream filestream) throws IOException {
        if (ImageFileExtension.isImageFileExtension(extension)) {
            return new ImageFileRender(extension, filestream);
        }
        return switch (extension) {
            case "ion" -> new IonFileRender(extension, filestream);
            case "md" -> new DefaultFileRender(extension, filestream, FileRender.TYPE.md);
            default -> new DefaultFileRender(extension, filestream);
        };
    }
}
