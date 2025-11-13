package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Indexer;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.services.SkipExecutionService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@CommandLine.Command(
    name = "webserver",
    description = "Start the Kestra webserver"
)
@Slf4j
public class WebServerCommand extends AbstractServerCommand {
    private ExecutorService poolExecutor;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Option(names = {"--no-tutorials"}, description = "Flag to disable auto-loading of tutorial flows.")
    private boolean tutorialsDisabled = false;

    @Option(names = {"--no-indexer"}, description = "Flag to disable starting an embedded indexer.")
    private boolean indexerDisabled = false;

    @CommandLine.Option(names = {"--skip-indexer-records"}, split=",", description = "a list of indexer record keys, separated by a coma; for troubleshooting only")
    private List<String> skipIndexerRecords = Collections.emptyList();

    @Override
    public boolean isFlowAutoLoadEnabled() {
        return !tutorialsDisabled;
    }

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.WEBSERVER
        );
    }

    @Override
    public Integer call() throws Exception {
        this.skipExecutionService.setSkipIndexerRecords(skipIndexerRecords);

        super.call();

        // start the indexer
        if (!indexerDisabled) {
            log.info("Starting an embedded indexer, this can be disabled by using `--no-indexer`.");
            poolExecutor = executorsUtils.cachedThreadPool("webserver-indexer");
            poolExecutor.execute(applicationContext.getBean(Indexer.class));
            shutdownHook(false, () -> poolExecutor.shutdown());
        }

        log.info("Webserver started");
        Await.until(() -> !this.applicationContext.isRunning());
        return 0;
    }
}
