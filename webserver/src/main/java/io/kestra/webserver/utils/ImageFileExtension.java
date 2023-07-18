package io.kestra.webserver.utils;

public enum ImageFileExtension {
    JPG("jpg"),
    JPEG("jpeg"),
    PNG("png"),
    SVG("svg"),
    GIF("gif"),
    BMP("bmp");

    private final String extension;

    ImageFileExtension(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
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