package io.kestra.core.utils;

public class WindowsUtils {

    public static String windowsToUnixPath(String path){
        String unixPath = path.replace("\\", "/").replace(":", "");
        if (!unixPath.startsWith("/")) {
            unixPath = "/" + unixPath;
        }
        return unixPath;
    }
}
