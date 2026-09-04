package io.kestra.jdbc.migration;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link V2_0_13KvMetadataUidMigration} against an in-memory H2 database.
 * Tests cover UID migration, idempotency, ambiguous namespace and key combinations,
 * empty values, and invalid metadata JSON.
 */
class V2_0_13KvMetadataUidMigrationTest {

    private static final Field<String> KEY_FIELD = DSL.field(DSL.quotedName("key"), String.class);
    private static final Field<String> VALUE_FIELD = DSL.field(DSL.quotedName("value"), String.class);
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private DSLContext dsl;
    private JooqDSLContextWrapper dslContextWrapper;
    private V2_0_13KvMetadataUidMigration migration;

    @BeforeEach
    void setUp() {
        // Each test gets its own isolated in-memory database via a unique name.
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        dsl = DSL.using(ds, SQLDialect.H2);
        // Column shape mirrors the real table closely enough for this migration: it only
        // ever reads/writes "key" and "value", so the value column is a plain TEXT blob
        // rather than the production JSONB type.
        dsl.execute("""
            CREATE TABLE kv_metadata (
                "key" VARCHAR(500) NOT NULL PRIMARY KEY,
                "value" TEXT NOT NULL
            )
            """);

        dslContextWrapper = new JooqDSLContextWrapper(dsl, (DataSource) ds);
        migration = new V2_0_13KvMetadataUidMigration(dslContextWrapper);
    }

    @Test
    void shouldMigrateKeyToComputedUid() throws Exception {
        // Given — a row stored under an arbitrary legacy key that predates uid()-based keys
        PersistedKvMetadata metadata = metadata("main", "company.team", "a", 1);
        insertRow("legacy-key-1", toJson(metadata));

        // When
        migration.migrate();

        // Then
        assertThat(fetchKeys()).containsExactly(metadata.uid());
    }

    @Test
    void shouldBeIdempotentOnSecondRun() throws Exception {
        // Given
        PersistedKvMetadata metadata = metadata("main", "company.team", "a", 1);
        insertRow("legacy-key-1", toJson(metadata));

        // When — migrate twice
        migration.migrate();
        migration.migrate();

        // Then — second run is a no-op, key is stable
        assertThat(fetchKeys()).containsExactly(metadata.uid());
    }

    @Test
    void shouldLeaveKeyUnchangedWhenAlreadyMatchesComputedUid() throws Exception {
        // Given — a row already stored under its correct, up-to-date uid
        PersistedKvMetadata metadata = metadata("main", "company.team", "a", 1);
        insertRow(metadata.uid(), toJson(metadata));

        // When
        migration.migrate();

        // Then
        assertThat(fetchKeys()).containsExactly(metadata.uid());
    }

    @Test
    void shouldMigrateEntriesWithAmbiguousNamespaceNameBoundariesToDistinctUids() throws Exception {
        // Given — two entries whose namespace/name split at different points around the same
        // characters. Under the old "_"-joined uid these collided into a single string; the
        // fixed "|"-joined uid() must keep them distinct.
        PersistedKvMetadata first = metadata("main", "company.team", "x_a", 1);
        PersistedKvMetadata second = metadata("main", "company.team_x", "a", 1);
        insertRow("legacy-key-1", toJson(first));
        insertRow("legacy-key-2", toJson(second));

        // When
        migration.migrate();

        // Then — both rows survive, under two distinct, correctly computed keys
        assertThat(first.uid()).isNotEqualTo(second.uid());
        assertThat(fetchKeys()).containsExactlyInAnyOrder(first.uid(), second.uid());
    }

    @Test
    void shouldSkipRowsWithEmptyValue() throws Exception {
        // Given — a row with a blank value (malformed/legacy state), and a normal row
        insertRow("legacy-key-empty", "");
        PersistedKvMetadata metadata = metadata("main", "company.team", "a", 1);
        insertRow("legacy-key-1", toJson(metadata));

        // When
        migration.migrate();

        // Then — the empty-value row is left untouched under its original key, the other is migrated
        assertThat(fetchKeys()).containsExactlyInAnyOrder("legacy-key-empty", metadata.uid());
    }

    @Test
    void shouldFailHardOnCorruptJson() {
        // Given
        insertRow("legacy-key-corrupt", "{not-valid-json");

        // When / Then — the migration must not silently skip corrupt rows, since that would let
        // the migration runner mark the script as applied and never retry it.
        assertThatThrownBy(() -> migration.migrate())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("failed to parse metadata");
    }

    private PersistedKvMetadata metadata(String tenantId, String namespace, String name, int revision) {
        return PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .name(name)
            .revision(revision)
            .created(Instant.now())
            .build();
    }

    private String toJson(PersistedKvMetadata metadata) throws Exception {
        return MAPPER.writeValueAsString(metadata);
    }

    private void insertRow(String key, String value) {
        dsl.insertInto(DSL.table("kv_metadata"))
            .set(KEY_FIELD, key)
            .set(VALUE_FIELD, value)
            .execute();
    }

    private java.util.List<String> fetchKeys() {
        return dsl.select(KEY_FIELD)
            .from(DSL.table("kv_metadata"))
            .fetch(KEY_FIELD);
    }
}