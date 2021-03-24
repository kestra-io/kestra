package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexerInterface;
import io.kestra.core.utils.Await;
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

        IndexerInterface indexer = applicationContext.getBean(IndexerInterface.class);
        indexer.run();

        log.info("Indexer started");

        this.shutdownHook(indexer::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
