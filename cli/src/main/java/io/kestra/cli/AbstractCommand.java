package io.kestra.cli;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.utils.Rethrow;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.inject.Inject;

@CommandLine.Command(
    mixinStandardHelpOptions = true
)
@Slf4j
abstract public class AbstractCommand implements Callable<Integer> {
    private final boolean withServer;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private EndpointDefaultConfiguration endpointConfiguration;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Change log level. Multiple -v options increase the verbosity.")
    private boolean[] verbose = new boolean[0];

    @CommandLine.Option(names = {"-l", "--log-level"}, description = "Change log level (values: ${COMPLETION-CANDIDATES}; default: ${DEFAULT-VALUE})")
    private LogLevel logLevel = LogLevel.INFO;

    @CommandLine.Option(names = {"--internal-log"}, description = "Change also log level for internal log, default: ${DEFAULT-VALUE})")
    private boolean internalLog = false;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to a configuration file, default: ${DEFAULT-VALUE})")
    private Path config = Paths.get(System.getProperty("user.home"), ".kestra/config.yml");

    @CommandLine.Option(names = {"-p", "--plugins"}, description = "Path to plugins directory , default: ${DEFAULT-VALUE})")
    protected Path pluginsPath = System.getenv("KESTRA_PLUGINS_PATH") != null ? Paths.get(System.getenv("KESTRA_PLUGINS_PATH")) : null;

    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public AbstractCommand(boolean withServer) {
        this.withServer = withServer;
    }

    @Override
    public Integer call() throws Exception {
        Thread.currentThread().setName(this.getClass().getDeclaredAnnotation(CommandLine.Command.class).name());
        startLogger();
        sendServerLog();
        startWebserver();

        return 0;
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

        if (this.withServer) {
            log.info("Starting Kestra with environments {}", applicationContext.getEnvironment().getActiveNames());
        }

        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
            .getLoggerList()
            .stream()
            .filter(logger -> (this.internalLog && logger.getName().startsWith("io.kestra")) || logger.getName().startsWith("flow"))
            .forEach(
                logger -> logger.setLevel(ch.qos.logback.classic.Level.valueOf(this.logLevel.name()))
            );
    }

    private void sendServerLog() {
        if (log.isTraceEnabled() && KestraClassLoader.instance().getPluginRegistry() != null) {
            KestraClassLoader.instance()
                .getPluginRegistry()
                .getPlugins()
                .forEach(c -> log.trace(c.toString()));
        }
    }

    private void startWebserver() {
        if (!this.withServer) {
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
            });
    }

    protected void shutdownHook(Rethrow.RunnableChecked<Exception> run) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warn("Receiving shutdown ! Try to graceful exit");

            try {
                run.run();
            } catch (Exception e) {
                log.error("Failed to close gracefully!", e);
            }
        }));
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

    @SuppressWarnings("unused")
    public PluginRegistry initPluginRegistry() {
        if (this.pluginsPath == null || !this.pluginsPath.toFile().exists()) {
            return null;
        }

        PluginScanner pluginScanner = new PluginScanner(KestraClassLoader.instance());
        List<RegisteredPlugin> scan = pluginScanner.scan(this.pluginsPath);

        PluginRegistry pluginRegistry = new PluginRegistry(scan);
        KestraClassLoader.instance().setPluginRegistry(pluginRegistry);

        return pluginRegistry;
    }
}
