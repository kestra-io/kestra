package org.kestra.cli;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@CommandLine.Command(
    mixinStandardHelpOptions = true
)
@Slf4j
abstract public class AbstractCommand implements Runnable {
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

    @CommandLine.Option(names = {"-c", "--config"}, description = "Used configuration file, default: ${DEFAULT-VALUE})")
    private Path config = Paths.get(System.getProperty("user.home"), ".kestra/config.yml");

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
    public void run() {
        Thread.currentThread().setName(this.getClass().getDeclaredAnnotation(CommandLine.Command.class).name());
        startLogger();
        startWebserver();
    }

    private void startLogger() {
        if (this.verbose.length == 1) {
            this.logLevel = LogLevel.DEBUG;
        } else if (this.verbose.length > 1) {
            this.logLevel = LogLevel.TRACE;
        }

        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
            .getLoggerList()
            .stream()
            .filter(logger -> (this.internalLog && logger.getName().indexOf("org.kestra") == 0) || logger.getName().indexOf("flow.") == 0)
            .forEach(
                logger -> logger.setLevel(ch.qos.logback.classic.Level.valueOf(this.logLevel.name()))
            );
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
