package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.Map;

@CommandLine.Command(
    name = "webserver",
    description = "start the webserver"
)
@Slf4j
public class WebServerCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Option(names = {"--no-tutorials"}, description = "Flag to disable auto-loading of tutorial flows.")
    boolean tutorialsDisabled = false;


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
        log.info("Webserver started");
        this.shutdownHook(() -> KestraContext.getContext().shutdown());
        Await.until(() -> !this.applicationContext.isRunning());
        return 0;
    }
}
