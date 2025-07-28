package io.kestra.core.storages.kv;

import io.kestra.core.storages.FileAttributes;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public record KVEntry(String key, String description, Instant creationDate, Instant updateDate, Instant expirationDate) {
    public static KVEntry from(FileAttributes fileAttributes) throws IOException {
        Optional<KVMetadata> kvMetadata = Optional.ofNullable(fileAttributes.getMetadata()).map(KVMetadata::new);
        return new KVEntry(
            fileAttributes.getFileName().replace(".ion", ""),
            kvMetadata.map(KVMetadata::getDescription).orElse(null),
            Instant.ofEpochMilli(fileAttributes.getCreationTime()),
            Instant.ofEpochMilli(fileAttributes.getLastModifiedTime()),
            kvMetadata.map(KVMetadata::getExpirationDate)
                .map(expirationDate -> expirationDate.truncatedTo(ChronoUnit.MILLIS))
                .orElse(null)
        );
    }
}
