package io.kestra.webserver.utils.filepreview;

import lombok.Getter;

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
        for (ImageFileExtension imageExtension : ImageFileExtension.values()) {
            if (imageExtension.getExtension().equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
}