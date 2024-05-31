package io.kestra.core.utils;

public class PathUtil {
    public static String checkLeadingSlash(String path) {
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;

    }
}
