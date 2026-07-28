package io.kestra.cli.commands.sys;

import java.time.Duration;
import java.util.Optional;

import io.kestra.cli.AbstractCommand;
import io.kestra.jdbc.runner.JdbcQueueCleaner;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "purge-queue",
    description = { "Purge expired messages from the JDBC 'queues' table",
        "Deletes records older than the configured 'kestra.jdbc.cleaner' retention. This command does not start any server component, so it can be run to recover an instance whose executor can no longer purge the queue on its own. Deletes are committed in batches to avoid lock contention on large tables."
    },
    mixinStandardHelpOptions = true
)
@Slf4j
public class PurgeQueueCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(
        names = { "--retention" },
        description = "Override the global retention for this run (ISO-8601 duration, e.g. PT1H). Per-type retentions from the configuration are always applied. Defaults to the configured 'kestra.jdbc.cleaner.retention'."
    )
    private Duration retention;

    @Override
    public Integer call() throws Exception {
        super.call();

        Optional<String> queueType = applicationContext.getProperty("kestra.queue.type", String.class);
        if (queueType.isEmpty()) {
            stdOut("Unable to purge the queue, the 'kestra.queue.type' configuration is not set");
            return 0;
        }

        switch (queueType.get()) {
            case "postgres", "mysql", "h2" -> {
                JdbcQueueCleaner queueCleaner = applicationContext.getBean(JdbcQueueCleaner.class);
                long purged = retention != null ? queueCleaner.purge(retention) : queueCleaner.purge();
                stdOut("Successfully purged {0} messages from the queues table", purged);
                return 0;
            }
            case "kafka" -> {
                stdOut("Unable to purge the queue, the 'kestra.queue.type' configuration is set to 'kafka' which does not use the 'queues' table");
                return 1;
            }
            default -> {
                stdOut("Unable to purge the queue, the 'kestra.queue.type' '{0}' does not use a purgeable 'queues' table", queueType.get());
                return 0;
            }
        }
    }
}
