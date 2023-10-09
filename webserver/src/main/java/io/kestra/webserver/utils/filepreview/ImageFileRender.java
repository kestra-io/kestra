package io.kestra.webserver.utils.filepreview;

import lombok.Getter;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;

@Getter
public class ImageFileRender extends FileRender {
    ImageFileRender(String extension, InputStream inputStream, Integer maxLine) throws IOException {
        super(extension, maxLine);
        this.content =  Base64.getEncoder().encodeToString(IOUtils.toByteArray(inputStream));
        this.type = Type.IMAGE;
    }

    @Getter
    public enum ImageFileExtension {
        JPG("jpg"),
        JPEG("jpeg"),
        PNG("png"),
        SVG("svg"),
        GIF("gif"),
        BMP("bmp"),
        WEBP("webp");

        private final String extension;

        ImageFileExtension(String extension) {
            this.extension = extension;
        }

        public static boolean isImageFileExtension(String extension) {
            return Arrays.stream(ImageFileExtension.values())
                .anyMatch(r -> r.getExtension().equalsIgnoreCase(extension));
        }
    }
}
