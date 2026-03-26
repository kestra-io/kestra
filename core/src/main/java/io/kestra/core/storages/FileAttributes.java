package io.kestra.core.storages;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
