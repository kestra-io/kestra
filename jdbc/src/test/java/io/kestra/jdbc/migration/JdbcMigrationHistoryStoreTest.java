package io.kestra.jdbc.migration;

import io.kestra.core.migration.MigrationScript;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JdbcMigrationHistoryStore} using an H2 in-memory database.
 * No Micronaut context is needed — the raw-DataSource constructor is used directly.
 */
class JdbcMigrationHistoryStoreTest {

    private JdbcMigrationHistoryStore store;

    @BeforeEach
    void setUp() throws Exception {
        // Each test gets its own isolated in-memory database via a unique name.
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        store = new JdbcMigrationHistoryStore(ds);
        store.bootstrapIfNeeded();
    }

    @Test
    void bootstrapIfNeeded_createsTable() throws Exception {
        // Table must exist after bootstrap — hasAnyApplied() would throw if it didn't
        assertThatNoException().isThrownBy(() -> store.hasAnyApplied());
    }

    @Test
    void isApplied_returnsFalseForUnknownScript() throws Exception {
        // Given: empty history store
        // When / Then
        assertThat(store.isApplied("2.0")).isFalse();
    }

    @Test
    void markApplied_thenIsApplied_returnsTrue() throws Exception {
        // Given
        MigrationScript script = script("2.0", "checksum-2.0");

        // When
        store.markApplied(script, 42L);

        // Then
        assertThat(store.isApplied("2.0")).isTrue();
    }

    @Test
    void hasAnyApplied_returnsFalseWhenEmpty_trueAfterMark() throws Exception {
        // Given: fresh database
        assertThat(store.hasAnyApplied()).isFalse();

        // When
        store.markApplied(script("2.0", "checksum-2.0"), 10L);

        // Then
        assertThat(store.hasAnyApplied()).isTrue();
    }

    @Test
    void validateChecksum_throwsOnMismatch() throws Exception {
        // Given: script applied with original checksum
        store.markApplied(script("2.0", "original-checksum"), 10L);

        // When / Then: same script ID, different checksum
        assertThatThrownBy(() -> store.validateChecksum(script("2.0", "tampered-checksum")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Checksum mismatch");
    }

    @Test
    void validateChecksum_passesOnMatch() throws Exception {
        // Given: script applied with a checksum
        store.markApplied(script("2.0", "correct-checksum"), 10L);

        // When / Then: same checksum — no exception
        assertThatNoException().isThrownBy(() -> store.validateChecksum(script("2.0", "correct-checksum")));
    }

    @Test
    void shouldReturnFalseForLegacyUpgradeOnFreshInstall() throws Exception {
        // Given: fresh H2 database with no flyway_schema_history table
        assertThat(store.detectLegacyUpgrade()).isFalse();
    }

    // --- Helpers ---

    private MigrationScript script(final String scriptId, final String checksum) {
        return new MigrationScript() {
            @Override public String scriptId() { return scriptId; }
            @Override public String description() { return "Migration " + scriptId; }
            @Override public String checksum() { return checksum; }
            @Override public void migrate() {}
        };
    }
}
