package io.kestra.cli.commands.servers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Indexer;
import io.kestra.core.services.IgnoreExecutionService;
import org.awaitility.Awaitility;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;
import io.kestra.core.utils.Await;

@CommandLine.Command(
    name = "indexer",
    description = "Start the Kestra indexer"
)
public class IndexerCommand extends AbstractServerCommand {
    @Inject
    private Indexer indexer;
    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @CommandLine.Option(names = { "--ignore-indexer-records" }, split = ",", description = "a list of indexer record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreIndexerRecords = Collections.emptyList();

    @CommandLine.Option(names = { "--ignore-queue-records" }, split = ",", description = "a list of queue record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreQueueRecords = Collections.emptyList();

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.INDEXER
        );
    }

    @Override
    public Integer call() throws Exception {
        this.ignoreExecutionService.setIgnoredIndexerRecords(ignoreIndexerRecords);
        this.ignoreExecutionService.setIgnoredQueueRecords(ignoreQueueRecords);

        super.call();

        indexer.run();

        Await.await().forever().until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
