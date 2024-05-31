package io.kestra.core.utils;

import java.util.regex.Matcher;

public class WindowsUtils {

    public static String windowsToUnixPath(String path){
        Matcher matcher = java.util.regex.Pattern.compile("([A-Za-z]:)").matcher(path);
        String unixPath = matcher.replaceAll(m -> m.group().toLowerCase());

        unixPath = unixPath
            .replace("\\", "/")
            .replace(":", "");
        if (!unixPath.startsWith("/")) {
            unixPath = "/" + unixPath;
        }
        return unixPath;
    }
}
