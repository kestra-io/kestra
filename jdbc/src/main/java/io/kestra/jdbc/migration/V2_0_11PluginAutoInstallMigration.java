package io.kestra.jdbc.migration;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import io.kestra.core.migration.MigrationScript;
import io.kestra.core.plugins.ExternalPluginsPath;
import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 1.3 → 2.0 first-sync migration: crawls the latest non-deleted revision of every flow, aggregates
 * the task/trigger types missing from the local plugin registry and bulk-installs their artifacts,
 * so an instance upgraded to a slim distribution does not fail every pre-existing flow.
 * Best-effort with a bounded wait: a failure is logged and never fails the startup.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0_11PluginAutoInstallMigration implements MigrationScript {

    private static final Field<String> TENANT_FIELD = DSL.field(DSL.quotedName("tenant_id"), String.class);
    private static final Field<String> NAMESPACE_FIELD = DSL.field(DSL.quotedName("namespace"), String.class);
    private static final Field<String> ID_FIELD = DSL.field(DSL.quotedName("id"), String.class);
    private static final Field<Integer> REVISION_FIELD = DSL.field(DSL.quotedName("revision"), Integer.class);
    private static final Field<Boolean> DELETED_FIELD = DSL.field(DSL.quotedName("deleted"), Boolean.class);
    private static final Field<String> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"), String.class);

    private final JooqDSLContextWrapper dslContextWrapper;
    private final Provider<PluginAutoInstallService> autoInstallService;
    private final Provider<PluginRegistry> pluginRegistry;

    @Inject
    public V2_0_11PluginAutoInstallMigration(
        final JooqDSLContextWrapper dslContextWrapper,
        final Provider<PluginAutoInstallService> autoInstallService,
        final Provider<PluginRegistry> pluginRegistry) {
        this.dslContextWrapper = dslContextWrapper;
        this.autoInstallService = autoInstallService;
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public String scriptId() {
        return "2.0.11-plugin-auto-install";
    }

    @Override
    public String description() {
        return "Auto-install the plugins referenced by existing flows that are missing from the local plugin registry";
    }

    @Override
    public String checksum() {
        // Java-only migration, no SQL resource file to checksum.
        return null;
    }

    @Override
    public void migrate() throws Exception {
        PluginAutoInstallService service = autoInstallService.get();
        if (!service.isEnabled()) {
            log.info("Plugin auto-install is disabled, skipping the first-sync plugin crawl.");
            return;
        }

        // Migrations run before AbstractCommand.maybeInitPlugins() registers the external plugins
        // directory — without this, every already-installed plugin would be re-downloaded.
        registerExternalPluginsDirectory();

        Set<String> missingTypes = findMissingTypesInAllFlows();
        if (missingTypes.isEmpty()) {
            log.info("All plugin types referenced by existing flows are available, nothing to install.");
            return;
        }

        log.info("Detected {} plugin types referenced by existing flows but missing from the local registry: {}.", missingTypes.size(), missingTypes);
        service.installMissingTypes(missingTypes);
    }

    private void registerExternalPluginsDirectory() {
        ExternalPluginsPath.fromEnvironment()
            .filter(Files::isDirectory)
            .ifPresent(path -> pluginRegistry.get().registerIfAbsent(path));
    }

    Set<String> findMissingTypesInAllFlows() {
        PluginAutoInstallService service = autoInstallService.get();
        return dslContextWrapper.transactionResult(configuration ->
        {
            // Unquoted on purpose: H2 folds the unquoted DDL name to upper case, so a quoted
            // lower-case reference would not resolve (same as the settings-table migrations).
            Table<?> flows = DSL.table("flows");
            // Same semantics as JdbcFlowRepositoryService.lastRevision + defaultFilter: the latest
            // revision is computed over ALL rows, then deleted ones are filtered out. Deleting the
            // last revision goes through deleteFlow (see deleteRevisions), so a deleted latest
            // revision means the whole flow is deleted and must not be crawled.
            Table<?> latest = DSL.select(
                TENANT_FIELD.as("latest_tenant_id"),
                NAMESPACE_FIELD.as("latest_namespace"),
                ID_FIELD.as("latest_id"),
                DSL.max(REVISION_FIELD).as("latest_revision")
            )
                .from(flows)
                .groupBy(TENANT_FIELD, NAMESPACE_FIELD, ID_FIELD)
                .asTable("latest");

            List<String> sources = DSL.using(configuration)
                .select(SOURCE_FIELD)
                .from(flows)
                .join(latest)
                .on(
                    TENANT_FIELD.isNotDistinctFrom(latest.field("latest_tenant_id", String.class)),
                    NAMESPACE_FIELD.eq(latest.field("latest_namespace", String.class)),
                    ID_FIELD.eq(latest.field("latest_id", String.class)),
                    REVISION_FIELD.eq(latest.field("latest_revision", Integer.class))
                )
                .where(DELETED_FIELD.isFalse())
                .fetch(SOURCE_FIELD);

            Set<String> missingTypes = new HashSet<>();
            for (String source : sources) {
                missingTypes.addAll(service.findMissingTypes(source));
            }
            return missingTypes;
        });
    }
}
