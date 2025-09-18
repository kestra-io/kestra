export function getJavaProjectNameFromBuildAbsolutePath(absoluteFilePath: string): string {
    const parts = absoluteFilePath.split("/");
    const buildIndex = parts.lastIndexOf("build");
    if (buildIndex > 0) {
        return parts[buildIndex - 1];
    }

    // return full path if not handled
    return absoluteFilePath;
}
