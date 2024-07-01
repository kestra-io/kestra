package io.kestra.core.storages.kv;

import io.kestra.core.storages.FileAttributes;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public record KVEntry(String key, Instant creationDate, Instant updateDate, Instant expirationDate) {
    public static KVEntry from(FileAttributes fileAttributes) throws IOException {
        return new KVEntry(
            fileAttributes.getFileName().replace(".ion", ""),
            Instant.ofEpochMilli(fileAttributes.getCreationTime()),
            Instant.ofEpochMilli(fileAttributes.getLastModifiedTime()),
            Optional.ofNullable(new KVMetadata(fileAttributes.getMetadata()).getExpirationDate())
                .map(expirationDate -> expirationDate.truncatedTo(ChronoUnit.MILLIS))
                .orElse(null)
        );
    }
}
