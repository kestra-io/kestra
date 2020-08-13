package org.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.ServerType;
import org.kestra.core.runners.Indexer;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import java.util.Map;
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

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.INDEXER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        Indexer indexer = applicationContext.getBean(Indexer.class);
        indexer.run();

        log.info("Indexer started");

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
