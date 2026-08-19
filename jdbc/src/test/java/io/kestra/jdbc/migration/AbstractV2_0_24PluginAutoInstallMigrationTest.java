package io.kestra.jdbc.migration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Abstract integration tests for {@link V2_0_24PluginAutoInstallMigration}.
 * Subclassed per JDBC backend (H2, Postgres, MySQL).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractV2_0_24PluginAutoInstallMigrationTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));
    private static final Field<Object> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"));
    private static final Field<String> NAMESPACE_FIELD = DSL.field(DSL.quotedName("namespace"), String.class);

    private static final String TEST_NAMESPACE = "plugin-auto-install-migration-test";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    private PluginAutoInstallService autoInstallService;
    private V2_0_24PluginAutoInstallMigration migration;

    @BeforeEach
    void setUp() {
        cleanup();
        autoInstallService = mock(PluginAutoInstallService.class);
        migration = new V2_0_24PluginAutoInstallMigration(
            dslContextWrapper,
            () -> autoInstallService,
            () -> mock(PluginRegistry.class)
        );
    }

    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("flows"))
                .where(NAMESPACE_FIELD.eq(TEST_NAMESPACE))
                .execute()
        );
    }

    @Test
    void shouldAggregateMissingTypesFromLatestNonDeletedRevisionsOnly() throws Exception {
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
    void shouldSkipWhenAutoInstallIsDisabled() throws Exception {
        // Given
        insertFlowRow(null, "flow-a", 1, false, "source-a-rev1");
        when(autoInstallService.isEnabled()).thenReturn(false);

        // When
        migration.migrate();

        // Then
        verify(autoInstallService, never()).findMissingTypes(any());
        verify(autoInstallService, never()).installMissingTypes(anySet());
    }

    // --- helpers ---

    private void insertFlowRow(String tenantId, String flowId, int revision, boolean deleted, String source) throws Exception {
        Map<String, Object> flowJson = new HashMap<>();
        flowJson.put("id", flowId);
        flowJson.put("namespace", TEST_NAMESPACE);
        flowJson.put("revision", revision);
        flowJson.put("deleted", deleted);
        if (tenantId != null) {
            flowJson.put("tenantId", tenantId);
        }
        String json = MAPPER.writeValueAsString(flowJson);
        String key = String.join("_", String.valueOf(tenantId), TEST_NAMESPACE, flowId, String.valueOf(revision));
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("flows"))
                .set(KEY_FIELD, (Object) key)
                .set(VALUE_FIELD, (Object) JdbcJsonbUtils.valueOf(json))
                .set(SOURCE_FIELD, (Object) source)
                .execute()
        );
    }
}
