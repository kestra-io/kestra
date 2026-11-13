package io.kestra.cli.commands.sys.database;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import javax.sql.DataSource;

import io.kestra.cli.AbstractCommand;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import io.micronaut.flyway.FlywayConfigurationProperties;
import picocli.CommandLine;

@CommandLine.Command(
    name = "plan",
    description = "Show the pending database schema migrations without applying them.\n" +
        "Kestra uses Flyway to manage database schema evolution; this command lists the migrations that 'sys database migrate' would run, then exits without modifying the database.",
    mixinStandardHelpOptions = true
)
@Slf4j
public class DatabasePlanCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = { "-s", "--sql" }, description = "Also print the SQL content of each pending migration.")
    private boolean sql = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        Collection<FlywayConfigurationProperties> configurations = applicationContext.getBeansOfType(FlywayConfigurationProperties.class);
        if (configurations.isEmpty()) {
            stdErr("No Flyway datasource is configured.");
            return 1;
        }

        DataSourceResolver dataSourceResolver = applicationContext.findBean(DataSourceResolver.class).orElse(DataSourceResolver.DEFAULT);

        int total = 0;
        for (FlywayConfigurationProperties configuration : configurations) {
            String name = configuration.getNameQualifier();

            // Only the datasource configured at runtime has a DataSource bean; skip the others.
            DataSource dataSource = applicationContext.findBean(DataSource.class, Qualifiers.byName(name)).orElse(null);
            if (dataSource == null) {
                continue;
            }

            // Unwrap the contextual proxy added by Micronaut Data to the underlying pooled DataSource;
            // otherwise Flyway fails with NoConnectionException as there is no transaction in scope.
            dataSource = dataSourceResolver.resolve(dataSource);

            // info() only reads the schema history table, it never modifies the database.
            Flyway flyway = configuration.getFluentConfiguration().dataSource(dataSource).load();
            MigrationInfo[] pending = flyway.info().pending();

            stdOut("@|bold Datasource '" + name + "'|@: " + pending.length + " pending migration(s).");
            if (pending.length == 0) {
                stdOut("  No pending migrations.");
                continue;
            }

            for (MigrationInfo migration : pending) {
                MigrationVersion version = migration.getVersion();
                stdOut(String.format(
                    "  %-10s %-9s %s — %s",
                    version != null ? version.getVersion() : "repeatable",
                    migration.getType(),
                    migration.getScript(),
                    migration.getDescription()
                ));

                if (sql) {
                    String content = readScript(configuration, migration);
                    if (content != null) {
                        stdOut("");
                        stdOut(content);
                        stdOut("");
                    }
                }
            }

            total += pending.length;
        }

        stdOut("@|bold Total|@: " + total + " pending migration(s).");
        return 0;
    }

    /**
     * Reads the SQL of a pending migration. Tries the physical file location first (when running from sources),
     * then falls back to the classpath resource (when running from the packaged jar).
     */
    private String readScript(FlywayConfigurationProperties configuration, MigrationInfo migration) {
        String physicalLocation = migration.getPhysicalLocation();
        if (physicalLocation != null) {
            Path path = Path.of(physicalLocation);
            if (Files.isReadable(path)) {
                try {
                    return Files.readString(path);
                } catch (Exception e) {
                    log.debug("Unable to read migration file {}", physicalLocation, e);
                }
            }
        }

        for (Location location : configuration.getFluentConfiguration().getLocations()) {
            String resource = location.getPath() + "/" + migration.getScript();
            try (InputStream is = applicationContext.getClassLoader().getResourceAsStream(resource)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.debug("Unable to read migration resource {}", resource, e);
            }
        }

        stdErr("  Unable to read SQL for " + migration.getScript());
        return null;
    }

    public static Map<String, Object> propertiesOverrides() {
        // Disable automatic Flyway migration on startup so this command can report the pending migrations
        // without applying them (the inverse of 'sys database migrate', which force-enables Flyway).
        return Map.of(
            "flyway.datasources.postgres.enabled", "false",
            "flyway.datasources.mysql.enabled", "false",
            "flyway.datasources.h2.enabled", "false"
        );
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }

    @Override
    protected boolean isPluginManagerEnabled() {
        return false;
    }
}
