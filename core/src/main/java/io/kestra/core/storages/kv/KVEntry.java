package io.kestra.core.storages.kv;

import io.kestra.core.storages.FileAttributes;

import java.io.IOException;
import java.time.Instant;

public record KVEntry(String key, Instant creationDate, Instant updateDate, Instant expirationDate) {
    public static KVEntry from(FileAttributes fileAttributes) throws IOException {
        return new KVEntry(
            fileAttributes.getFileName().replace(".ion", ""),
            Instant.ofEpochMilli(fileAttributes.getCreationTime()),
            Instant.ofEpochMilli(fileAttributes.getLastModifiedTime()),
            new KVMetadata(fileAttributes.getMetadata()).getExpirationDate()
        );
    }
}
