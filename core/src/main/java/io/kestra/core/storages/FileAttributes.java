package io.kestra.core.storages;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

@JsonSerialize(as = FileAttributes.class)
public interface FileAttributes {
    String getFileName();

    long getLastModifiedTime();

    long getCreationTime();

    FileType getType();

    long getSize();

    Map<String, String> getMetadata() throws IOException;

    enum FileType {
        File,
        Directory
    }
}
