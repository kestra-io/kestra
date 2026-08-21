package io.kestra.jdbc.migration;

import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.jdbc.JooqDSLContextWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link V2_0_25PluginAutoInstallMigration} against an in-memory H2 database.
 * The crawl query is plain jOOQ, so a single backend is enough — no per-DB subclasses.
 */
class V2_0_25PluginAutoInstallMigrationTest {

    private static final Field<String> KEY_FIELD = DSL.field(DSL.quotedName("key"), String.class);
    private static final Field<String> TENANT_FIELD = DSL.field(DSL.quotedName("tenant_id"), String.class);
    private static final Field<String> NAMESPACE_FIELD = DSL.field(DSL.quotedName("namespace"), String.class);
    private static final Field<String> ID_FIELD = DSL.field(DSL.quotedName("id"), String.class);
    private static final Field<Integer> REVISION_FIELD = DSL.field(DSL.quotedName("revision"), Integer.class);
    private static final Field<Boolean> DELETED_FIELD = DSL.field(DSL.quotedName("deleted"), Boolean.class);
    private static final Field<String> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"), String.class);

    private static final String TEST_NAMESPACE = "plugin-auto-install-migration-test";

    private JooqDSLContextWrapper dslContextWrapper;
    private DSLContext dsl;
    private PluginAutoInstallService autoInstallService;
    private V2_0_25PluginAutoInstallMigration migration;

    @BeforeEach
    void setUp() {
        // Each test gets its own isolated in-memory database via a unique name.
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        dsl = DSL.using(ds, SQLDialect.H2);
        // Same column shape as the real baseline: unquoted table name, quoted lower-case columns.
        dsl.execute("""
            CREATE TABLE flows (
                "key" VARCHAR(250) NOT NULL PRIMARY KEY,
                "deleted" BOOL NOT NULL,
                "id" VARCHAR(100) NOT NULL,
                "namespace" VARCHAR(150) NOT NULL,
                "revision" INT NOT NULL,
                "source_code" TEXT NOT NULL,
                "tenant_id" VARCHAR(250)
            )
            """);

        dslContextWrapper = new JooqDSLContextWrapper(dsl, (DataSource) ds);
        autoInstallService = mock(PluginAutoInstallService.class);
        migration = new V2_0_25PluginAutoInstallMigration(
            dslContextWrapper,
            () -> autoInstallService,
            () -> mock(PluginRegistry.class)
        );
    }

    @Test
    void shouldAggregateMissingTypesFromLatestNonDeletedRevisionsOnly() {
        // Given — flow-a has two revisions: only the latest one must be crawled
        insertFlowRow(null, "flow-a", 1, false, "source-a-rev1");
        insertFlowRow(null, "flow-a", 2, false, "source-a-rev2");
        // flow-b is deleted (latest revision carries deleted=true): it must be excluded entirely
        insertFlowRow(null, "flow-b", 1, false, "source-b-rev1");
        insertFlowRow(null, "flow-b", 2, true, "source-b-rev2");
        // flow-c lives in a tenant: the null-safe tenant join must still pick it up
        insertFlowRow("tenant-1", "flow-c", 1, false, "source-c-rev1");

        when(autoInstallService.findMissingTypes("source-a-rev2")).thenReturn(Set.of("io.kestra.plugin.a.TaskA"));
        when(autoInstallService.findMissingTypes("source-c-rev1")).thenReturn(Set.of("io.kestra.plugin.c.TaskC"));

        // When
        Set<String> missingTypes = migration.findMissingTypesInAllFlows();

        // Then — the union of the latest non-deleted revisions, nothing else crawled
        assertThat(missingTypes).containsExactlyInAnyOrder("io.kestra.plugin.a.TaskA", "io.kestra.plugin.c.TaskC");
        verify(autoInstallService, never()).findMissingTypes("source-a-rev1");
        verify(autoInstallService, never()).findMissingTypes("source-b-rev1");
        verify(autoInstallService, never()).findMissingTypes("source-b-rev2");
    }

    @Test
    void shouldInstallAggregatedMissingTypesWhenEnabled() throws Exception {
        // Given
        insertFlowRow(null, "flow-a", 1, false, "source-a-rev1");
        when(autoInstallService.isEnabled()).thenReturn(true);
        when(autoInstallService.findMissingTypes("source-a-rev1")).thenReturn(Set.of("io.kestra.plugin.a.TaskA"));

        // When
        migration.migrate();

        // Then
        verify(autoInstallService).installMissingTypes(Set.of("io.kestra.plugin.a.TaskA"));
    }

    @Test
    void shouldNotInstallWhenNoTypeIsMissing() throws Exception {
        // Given
        insertFlowRow(null, "flow-a", 1, false, "source-a-rev1");
        when(autoInstallService.isEnabled()).thenReturn(true);
        when(autoInstallService.findMissingTypes(any())).thenReturn(Set.of());

        // When
        migration.migrate();

        // Then
        verify(autoInstallService, never()).installMissingTypes(anySet());
    }

    @Test
    void shouldDoNothingWhenAutoInstallIsDisabled() throws Exception {
        // Given
        insertFlowRow(null, "flow-a", 1, false, "source-a-rev1");
        when(autoInstallService.isEnabled()).thenReturn(false);

        // When
        migration.migrate();

        // Then
        verify(autoInstallService, never()).installMissingTypes(anySet());
    }

    private void insertFlowRow(String tenantId, String flowId, int revision, boolean deleted, String source) {
        String key = String.join("_", String.valueOf(tenantId), TEST_NAMESPACE, flowId, String.valueOf(revision));
        dsl.insertInto(DSL.table("flows"))
            .set(KEY_FIELD, key)
            .set(TENANT_FIELD, tenantId)
            .set(NAMESPACE_FIELD, TEST_NAMESPACE)
            .set(ID_FIELD, flowId)
            .set(REVISION_FIELD, revision)
            .set(DELETED_FIELD, deleted)
            .set(SOURCE_FIELD, source)
            .execute();
    }
}
