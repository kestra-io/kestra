package io.kestra.core.utils;

import java.net.URI;
import java.util.regex.Matcher;

public class WindowsUtils {

    public static String windowsToUnixPath(String path, boolean startWithSlash) {
        Matcher matcher = java.util.regex.Pattern.compile("([A-Za-z]:)").matcher(path);
        String unixPath = matcher.replaceAll(m -> m.group().toLowerCase());

        unixPath = unixPath
            .replace("\\", "/")
            .replace(":", "");
        if (!unixPath.startsWith("/") && startWithSlash) {
            unixPath = "/" + unixPath;
        }
        return unixPath;
    }

    public static String windowsToUnixPath(String path) {
        return windowsToUnixPath(path, true);
    }

    public static URI windowsToUnixURI(URI uri) {

        return URI.create(windowsToUnixPath(uri.toString(), false));

    }
}
