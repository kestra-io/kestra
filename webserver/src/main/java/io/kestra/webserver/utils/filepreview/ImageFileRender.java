package io.kestra.webserver.utils.filepreview;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Getter
public class ImageFileRender extends Base64Render {
    ImageFileRender(String extension, InputStream inputStream, Integer maxLine) throws IOException {
        super(extension, inputStream, maxLine);
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
