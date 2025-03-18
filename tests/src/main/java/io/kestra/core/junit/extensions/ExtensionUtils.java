package io.kestra.core.junit.extensions;

import java.net.URL;

public final class ExtensionUtils {

    private ExtensionUtils(){}

    public static URL loadFile(String path) {
        URL resource = ExtensionUtils.class.getClassLoader().getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to load flow: " + path);
        }
        return resource;
    }

}
