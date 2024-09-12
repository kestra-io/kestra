package io.kestra.cli;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.ImmutableMap;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.cli.services.StartupHookInterface;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.webserver.services.FlowAutoLoaderService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import io.kestra.core.utils.Rethrow;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.Callable;
import jakarta.inject.Inject;

@CommandLine.Command(
    versionProvider = VersionProvider.class,
    mixinStandardHelpOptions = true,
    showDefaultValues = true
)
@Slf4j
@Introspected
abstract public class AbstractCommand implements Callable<Integer> {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private EndpointDefaultConfiguration endpointConfiguration;

    @Inject
    private StartupHookInterface startupHook;

    private PluginRegistry pluginRegistry;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Change log level. Multiple -v options increase the verbosity.", showDefaultValue = CommandLine.Help.Visibility.NEVER)
    private boolean[] verbose = new boolean[0];

    @CommandLine.Option(names = {"-l", "--log-level"}, description = "Change log level (values: ${COMPLETION-CANDIDATES})")
    private LogLevel logLevel = LogLevel.INFO;

    @CommandLine.Option(names = {"--internal-log"}, description = "Change also log level for internal log")
    private boolean internalLog = false;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to a configuration file")
    private Path config = Paths.get(System.getProperty("user.home"), ".kestra/config.yml");

    @CommandLine.Option(names = {"-p", "--plugins"}, description = "Path to plugins directory")
    protected Path pluginsPath = System.getenv("KESTRA_PLUGINS_PATH") != null ? Paths.get(System.getenv("KESTRA_PLUGINS_PATH")) : null;

    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @Override
    public Integer call() throws Exception {
        Thread.currentThread().setName(this.getClass().getDeclaredAnnotation(CommandLine.Command.class).name());
        startLogger();
        sendServerLog();
        if (this.startupHook != null) {
            this.startupHook.start(this);
        }

        if (this.pluginsPath != null && loadExternalPlugins()) {
            pluginRegistry = pluginRegistry();
            pluginRegistry.registerIfAbsent(pluginsPath);
        }

        startWebserver();
        return 0;
    }

    /**
     * Specifies whether external plugins must be loaded.
     * This method can be overridden by concrete commands.
     *
     * @return {@code true} if external plugins must be loaded.
     */
    protected boolean loadExternalPlugins() {
        return true;
    }

    protected PluginRegistry pluginRegistry() {
        return KestraContext.getContext().getPluginRegistry(); // Lazy init
    }

    private static String message(String message, Object... format) {
        return CommandLine.Help.Ansi.AUTO.string(
            format.length == 0 ? message : MessageFormat.format(message, format)
        );
    }

    protected static void stdOut(String message, Object... format) {
        System.out.println(message(message, format));
    }

    protected static void stdErr(String message, Object... format) {
        System.err.println(message(message, format));
    }

    private void startLogger() {
        if (this.verbose.length == 1) {
            this.logLevel = LogLevel.DEBUG;
        } else if (this.verbose.length > 1) {
            this.logLevel = LogLevel.TRACE;
        }

        if (this instanceof ServerCommandInterface) {
            log.info("Starting Kestra with environments {}", applicationContext.getEnvironment().getActiveNames());
        }

        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
            .getLoggerList()
            .stream()
            .filter(logger ->
                (
                    this.internalLog && (
                        logger.getName().startsWith("io.kestra") &&
                            !logger.getName().startsWith("io.kestra.ee.runner.kafka.services"))
                )
                    || logger.getName().startsWith("flow")
            )
            .forEach(
                logger -> logger.setLevel(ch.qos.logback.classic.Level.valueOf(this.logLevel.name()))
            );
    }

    private void sendServerLog() {
        if (log.isTraceEnabled() && pluginRegistry != null) {
            pluginRegistry.plugins().forEach(c -> log.trace(c.toString()));
        }
    }

    private void startWebserver() {
        if (!(this instanceof ServerCommandInterface)) {
            return;
        }

        applicationContext
            .findBean(EmbeddedServer.class)
            .ifPresent(server -> {
                server.start();

                if (this.endpointConfiguration.getPort().isPresent()) {
                    URI endpoint = null;
                    try {
                        endpoint = new URIBuilder(server.getURL().toURI())
                            .setPort(this.endpointConfiguration.getPort().get())
                            .setPath("/health")
                            .build();
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    log.info("Server Running: {}, Management server on port {}", server.getURL(), endpoint);
                } else {
                    log.info("Server Running: {}", server.getURL());
                }

                if (isFlowAutoLoadEnabled()) {
                    applicationContext
                        .findBean(FlowAutoLoaderService.class)
                        .ifPresent(FlowAutoLoaderService::load);
                }
            });
    }

    public boolean isFlowAutoLoadEnabled() {
        return false;
    }

    protected void shutdownHook(Rethrow.RunnableChecked<Exception> run) {
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                log.warn("Receiving shutdown ! Try to graceful exit");
                try {
                    run.run();
                } catch (Exception e) {
                    log.error("Failed to close gracefully!", e);
                }
            },
            "command-shutdown"
        ));
    }

    @SuppressWarnings({"unused"})
    public Map<String, Object> propertiesFromConfig() {
        if (this.config.toFile().exists()) {
            YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader();

            try {
                return yamlPropertySourceLoader.read("cli", new FileInputStream(this.config.toFile()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ImmutableMap.of();
    }
}
