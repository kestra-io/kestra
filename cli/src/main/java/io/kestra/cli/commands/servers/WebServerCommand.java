package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Indexer;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.worker.Controller;
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
    private IgnoreExecutionService ignoreExecutionService;

    @Option(names = {"--no-tutorials"}, description = "Flag to disable auto-loading of tutorial flows.")
    private boolean tutorialsDisabled = false;

    @Option(names = {"--no-indexer"}, description = "Flag to disable starting an embedded indexer.")
    private boolean indexerDisabled = false;

    @Option(names = {"--no-controller"}, description = "Flag to disable starting an embedded controller.")
    private boolean controllerDisabled = false;

    @CommandLine.Option(names = {"--ignore-indexer-records"}, split=",", description = "a list of indexer record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreIndexerRecords = Collections.emptyList();

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
        this.ignoreExecutionService.setIgnoredIndexerRecords(ignoreIndexerRecords);

        super.call();

        if (!(indexerDisabled && controllerDisabled)) {
            poolExecutor = executorsUtils.cachedThreadPool("embedded-services");
        }

        // start the indexer
        if (!indexerDisabled) {
            log.info("Starting an embedded indexer, this can be disabled by using `--no-indexer`.");
            poolExecutor.execute(applicationContext.getBean(Indexer.class));
        }

        // start the controller
        if (!controllerDisabled) {
            log.info("Starting an embedded controller, this can be disabled by using `--no-controller`.");
            Controller controller = applicationContext.getBean(Controller.class);
            poolExecutor.execute(controller::start);
        }

        if (poolExecutor != null) {
            shutdownHook(true, () -> poolExecutor.shutdown());
        }

        log.info("Webserver started");
        Await.until(() -> !this.applicationContext.isRunning());
        return 0;
    }
}
