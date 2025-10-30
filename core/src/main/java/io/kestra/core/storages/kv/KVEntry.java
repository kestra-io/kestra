package io.kestra.core.storages.kv;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.storages.FileAttributes;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record KVEntry(String namespace, String key, Integer version, @Nullable String description, Instant creationDate, Instant updateDate, @Nullable Instant expirationDate) {
    private static final Pattern captureKeyAndVersion = Pattern.compile("(.*)\\.ion(?:\\.v(\\d+))?$");

    public static KVEntry from(String namespace, FileAttributes fileAttributes) throws IOException {
        Optional<KVMetadata> kvMetadata = Optional.ofNullable(fileAttributes.getMetadata()).map(KVMetadata::new);
        String fileName = fileAttributes.getFileName();
        Matcher matcher = captureKeyAndVersion.matcher(fileName);
        if (!matcher.matches()) {
            throw new IOException("Invalid KV file name format: " + fileName);
        }
        return new KVEntry(
            namespace,
            matcher.group(1),
            Optional.ofNullable(matcher.group(2)).map(Integer::parseInt).orElse(1),
            kvMetadata.map(KVMetadata::getDescription).orElse(null),
            Instant.ofEpochMilli(fileAttributes.getCreationTime()),
            Instant.ofEpochMilli(fileAttributes.getLastModifiedTime()),
            kvMetadata.map(KVMetadata::getExpirationDate)
                .map(expirationDate -> expirationDate.truncatedTo(ChronoUnit.MILLIS))
                .orElse(null)
        );
    }

    public static KVEntry from(PersistedKvMetadata persistedKvMetadata) throws IOException {
        return new KVEntry(persistedKvMetadata.getNamespace(), persistedKvMetadata.getName(), persistedKvMetadata.getVersion(), persistedKvMetadata.getDescription(), persistedKvMetadata.getCreated(), persistedKvMetadata.getUpdated(), persistedKvMetadata.getExpirationDate());
    }
}
