package io.kestra.cli.commands.sys.database;

import io.kestra.cli.AbstractCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "migrate",
    description = "Force database schema migration.\nKestra uses Flyway to manage database schema evolution, this command will run Flyway then exit.",
    mixinStandardHelpOptions = true
)
@Slf4j
public class DatabaseMigrateCommand extends AbstractCommand {
    @Override
    public Integer call() throws Exception {
        // Flyway will run automatically
        super.call();
        stdOut("Successfully run the database schema migration.");
        return 0;
    }

    public static Map<String, Object> propertiesOverrides() {
        // Forcing the props to enabled Flyway: it allows to disable Flyway globally and still using this command.
        return Map.of(
            "flyway.datasources.postgres.enabled", "true",
            "flyway.datasources.mysql.enabled", "true",
            "flyway.datasources.h2.enabled", "true"
        );
    }
}
