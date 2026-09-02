package io.kestra.jdbc.migration;

import io.kestra.core.models.kv.PersistedKvMetadata;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.migration.MigrationScript;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Migrates KV Store metadata records to use the {@code |} (pipe) delimiter instead of
 * {@code _} (underscore) in the {@code uid}.
 *
 * <p>
 * The {@code kv_metadata} table stores each entry under a {@code key} column whose value
 * is the record's {@code uid}. The {@code value} column contains the serialized
 * {@link PersistedKvMetadata}, whose {@link PersistedKvMetadata#uid()} reflects the
 * current UID format.
 *
 * <p>
 * For each row, this migration deserializes the stored metadata, recomputes the expected
 * {@code uid} using {@code metadata.uid()}, and updates the row's {@code key} when the
 * computed UID differs from the stored key.
 *
 * <p>
 * Idempotency: if the stored {@code key} already matches {@code metadata.uid()}, the row
 * is left unchanged.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0_13KvMetadataUidMigration implements MigrationScript {

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public V2_0_13KvMetadataUidMigration(final JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    @Override
    public String scriptId() {
        return "2.0.13-kv-metadata-uid-migration";
    }

    @Override
    public String description() {
        return "Migrate KV Store metadata to the collision-safe UID format";
    }

    @Override
    public String checksum() {
        // Java-only migration – no SQL resource file, checksum validation not applicable.
        return null;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration -> {
            var records = DSL.using(configuration)
                .select(KEY_FIELD, VALUE_FIELD)
                .from(DSL.table("kv_metadata"))
                .fetch();

            if (records.isEmpty()) {
                log.info("KV metadata UID migration: no metadata found, skipping.");
                return;
            }

            // Queries are build during the loop, then executed together via batch()
            List<Query> updates = new ArrayList<>();

            for (var record : records) {
                String oldKey = record.get(KEY_FIELD, String.class);
                String json = record.get(VALUE_FIELD, String.class);

                if (json == null || json.isBlank()) {
                    log.warn(
                        "KV metadata UID migration: key [{}] has an empty value, skipping.",
                        oldKey
                    );
                    continue;
                }

                PersistedKvMetadata metadata;
                try {
                    metadata = MAPPER.readValue(json, PersistedKvMetadata.class);
                } catch (IOException e) {
                    // Corrupt JSON must not be silently skipped: the migration runner would mark the
                    // script as applied and skip it on future restarts, leaving this row's key
                    // permanently unmigrated. Fail hard so the operator is forced to fix the row.
                    log.error(
                        "KV metadata UID migration: failed to parse metadata for key [{}].",
                        oldKey,
                        e
                    );
                    throw new RuntimeException(
                        "KV metadata UID migration: failed to parse metadata",
                        e
                    );
                }

                // metadata.uid() is the source of truth for the current delimiter convention;
                // recomputing it avoids mangling namespaces or keys that legitimately contain an underscore.
                String newKey = metadata.uid();

                if (newKey == null || newKey.isBlank()) {
                    log.error("KV metadata UID migration: computed a null/blank uid for key [{}], skipping.", oldKey);
                    throw new RuntimeException("KV metadata UID migration: computed null uid for key " + oldKey);
                }

                if (oldKey.equals(newKey)) {
                    log.debug(
                        "KV metadata UID migration: key [{}] is already up to date, skipping.",
                        oldKey
                    );
                    continue;
                }

                updates.add(
                    DSL.using(configuration)
                        .update(DSL.table("kv_metadata"))
                        .set(KEY_FIELD, newKey)
                        .where(KEY_FIELD.eq(oldKey))
                );

                log.info(
                    "KV metadata UID migration: migrated [{}] -> [{}].",
                    oldKey,
                    newKey
                );
            }

            if (updates.isEmpty()) {
                log.info("KV metadata UID migration: nothing to migrate, all keys already up to date.");
            }

            int[] results = DSL.using(configuration).batch(updates).execute();
            log.info("KV metadata UID migration: batch-updated {} rows.", results.length);
        });
    }
}