package io.kestra.core.utils;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;

public class WindowsUtils {

    public static String windowsToUnixPath(String path, boolean startWithSlash) {
        // Only normalize a leading Windows drive letter (e.g. "C:") so colons in filenames are preserved.
        Matcher matcher = java.util.regex.Pattern.compile("^([A-Za-z]):").matcher(path);
        String unixPath = matcher.replaceAll(m -> m.group(1).toLowerCase(Locale.ROOT));

        unixPath = unixPath.replace("\\", "/");
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
