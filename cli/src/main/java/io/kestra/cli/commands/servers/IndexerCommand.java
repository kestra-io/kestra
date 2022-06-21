package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.IndexerInterface;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "indexer",
    description = "start an indexer"
)
@Slf4j
public class IndexerCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

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
