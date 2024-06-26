package io.kestra.storage.local;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import static io.kestra.core.storages.FileAttributes.FileType.*;

@Value
@Builder
public class LocalFileAttributes implements FileAttributes {
    Path filePath;

    BasicFileAttributes basicFileAttributes;

    @Override
    public String getFileName() {
        return filePath.getFileName().toString();
    }

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
            throw new RuntimeException("Unknown type for file %s".formatted(getFileName()));
        }
    }

    @Override
    public long getSize() {
        return basicFileAttributes.size();
    }

    @Override
    public Map<String, String> getMetadata() throws IOException {
        return LocalFileAttributes.getMetadata(this.filePath);
    }

    public static Map<String, String> getMetadata(Path filePath) throws IOException {
        File metadataFile = new File(filePath.toString() + ".metadata");
        if (metadataFile.exists()) {
            try(InputStream is = new FileInputStream(metadataFile)){
                String metadataFileContent = new String(is.readAllBytes());
                return JacksonMapper.ofIon().readValue(metadataFileContent, new TypeReference<>() {
                });
            }
        }

        return null;
    }
}
