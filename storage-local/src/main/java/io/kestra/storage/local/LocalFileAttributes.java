package io.kestra.storage.local;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.storages.FileAttributes;
import lombok.Builder;
import lombok.Value;

import javax.naming.directory.InvalidAttributesException;
import java.nio.file.attribute.BasicFileAttributes;

import static io.kestra.core.storages.FileAttributes.FileType.*;

@Value
@Builder
public class LocalFileAttributes implements FileAttributes {
    String fileName;

    BasicFileAttributes basicFileAttributes;

    @Override
    public long getLastModifiedTime() {
        return basicFileAttributes.lastModifiedTime().toMillis();
    }

    @Override
    public long getCreationTime() {
        return basicFileAttributes.creationTime().toMillis();
    }

    @Override
    public FileType getType() {
        if (basicFileAttributes.isRegularFile()) {
            return File;
        } else if (basicFileAttributes.isDirectory()) {
            return Directory;
        } else {
            throw new RuntimeException("Unknown type for file %s".formatted(fileName));
        }
    }

    @Override
    public long getSize() {
        return basicFileAttributes.size();
    }
}
