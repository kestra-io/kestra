package org.kestra.cli.commands.servers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.runners.Indexer;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;

@CommandLine.Command(
    name = "indexer",
    description = "start an indexer"
)
@Slf4j
public class IndexerCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public IndexerCommand() {
        super(true);
    }

    @Override
    public void run() {
        super.run();

        Indexer indexer = applicationContext.getBean(Indexer.class);
        indexer.run();

        log.info("Indexer started");

        Await.until(() -> !this.applicationContext.isRunning());
    }
}