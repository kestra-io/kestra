package io.kestra.core.storages;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(as = FileAttributes.class)
public interface FileAttributes {
    String getFileName();

    long getLastModifiedTime();

    long getCreationTime();

    FileType getType();

    long getSize();

    enum FileType {
        File,
        Directory
    }
}
