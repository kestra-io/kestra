package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.runners.IndexerInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

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

    @Option(names = {"--no-tutorials"}, description = "Flag to disable auto-loading of tutorial flows.")
    boolean tutorialsDisabled = false;

    @Option(names = {"--no-indexer"}, description = "Flag to disable starting an embedded indexer.")
    boolean indexerDisabled = false;

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
        super.call();

        // start the indexer
        if (!indexerDisabled) {
            log.info("Starting an embedded indexer, this can be disabled by using `--no-indexer`.");
            poolExecutor = executorsUtils.cachedThreadPool("webserver-indexer");
            poolExecutor.execute(applicationContext.getBean(IndexerInterface.class));
        }

        log.info("Webserver started");
        this.shutdownHook(() -> {
            this.close();
            KestraContext.getContext().shutdown();
        });
        Await.until(() -> !this.applicationContext.isRunning());
        return 0;
    }

    private void close() {
        if (this.poolExecutor != null) {
            this.poolExecutor.shutdown();
        }
    }
}
