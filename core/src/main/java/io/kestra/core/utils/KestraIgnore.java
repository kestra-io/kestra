package io.kestra.core.utils;

import nl.basjes.gitignore.GitIgnore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class KestraIgnore {
    public static final String KESTRA_IGNORE_FILE_NAME = ".kestraignore";

    private GitIgnore gitIgnore;
    private Path rootFolderPath;

    public KestraIgnore(Path rootFolderPath) throws IOException {
        this.rootFolderPath = rootFolderPath;
        File kestraIgnoreFile = rootFolderPath.resolve(KESTRA_IGNORE_FILE_NAME).toFile();
        gitIgnore = kestraIgnoreFile.exists()
            ? new GitIgnore(kestraIgnoreFile)
            : new GitIgnore("");
    }

    public boolean isIgnoredFile(String path, boolean ignoreKestraIgnoreFile) {
        if (path.equals(this.rootFolderPath.resolve(KESTRA_IGNORE_FILE_NAME).toString()) && ignoreKestraIgnoreFile) {
            return true;
        }

        return Boolean.TRUE.equals(gitIgnore.isIgnoredFile(path));
    }
}