package io.kestra.cli;

import com.google.common.collect.ImmutableMap;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.cli.services.StartupHookInterface;
import io.kestra.core.plugins.PluginManager;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.webserver.services.FlowAutoLoaderService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.utils.Rethrow;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Introspected
public abstract class AbstractCommand extends BaseCommand implements Callable<Integer> {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    private EndpointDefaultConfiguration endpointConfiguration;

    @Inject
    private StartupHookInterface startupHook;

    @Inject
    private io.kestra.core.utils.VersionProvider versionProvider;

    @Inject
    protected Provider<PluginRegistry> pluginRegistryProvider;

    @Inject
    protected Provider<PluginManager> pluginManagerProvider;

    protected PluginRegistry pluginRegistry;

    @Option(names = {"-c", "--config"}, description = "Path to a configuration file")
    private Path config = Paths.get(System.getProperty("user.home"), ".kestra/config.yml");

    @Option(names = {"-p", "--plugins"}, description = "Path to plugins directory")
    protected Path pluginsPath = Optional.ofNullable(System.getenv("KESTRA_PLUGINS_PATH")).map(Paths::get).orElse(null);

    @Override
    public Integer call() throws Exception {
        Thread.currentThread().setName(this.getClass().getDeclaredAnnotation(Command.class).name());
        initLogger();
        sendServerLog();
        if (this.startupHook != null) {
            this.startupHook.start(this);
        }

        maybeInitPlugins();
        maybeStartWebserver();
        return 0;
    }

    /**
     * Initializes the plugin registry.
     */
    protected void maybeInitPlugins() {
        if (pluginRegistryProvider != null && this.pluginsPath != null && loadExternalPlugins()) {
            pluginRegistry = pluginRegistryProvider.get();
            pluginRegistry.registerIfAbsent(pluginsPath);

            // PluginManager must only be initialized if a registry is also instantiated
            if (isPluginManagerEnabled()) {
                PluginManager manager = pluginManagerProvider.get();
                manager.start();
            }
        }
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

    /**
     * Specifies whether the {@link PluginManager} service must be initialized.
     * <p>
     * This method can be overridden by concrete commands.
     *
     * @return {@code true} if the {@link PluginManager} service must be initialized.
     */
    protected boolean isPluginManagerEnabled() {
        return true;
    }

    @Override
    protected void initLogger() {
        if (this instanceof ServerCommandInterface) {
            String buildInfo = "";
            if (versionProvider.getRevision() != null) {
                buildInfo += " [revision " + versionProvider.getRevision();

                if (versionProvider.getDate() != null) {
                    buildInfo += " / " + versionProvider.getDate().toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
                }

                buildInfo += "]";
            }

            log.info(
                "Starting Kestra {} with environments {}{}",
                versionProvider.getVersion(),
                applicationContext.getEnvironment().getActiveNames(),
                buildInfo
            );
        }
        super.initLogger();
    }

    private void sendServerLog() {
        if (log.isTraceEnabled() && pluginRegistry != null) {
            pluginRegistry.plugins().forEach(c -> log.trace(c.toString()));
        }
    }

    private void maybeStartWebserver() {
        if (!(this instanceof ServerCommandInterface)) {
            return;
        }

        applicationContext
            .findBean(EmbeddedServer.class)
            .ifPresent(server -> {
                server.start();

                if (this.endpointConfiguration.getPort().isPresent()) {
                    URI managementEndpoint = null;
                    URI healthEndpoint = null;
                    try {
                        managementEndpoint = UriBuilder.of(server.getURL().toURI())
                            .port(this.endpointConfiguration.getPort().get())
                            .build();
                        healthEndpoint = managementEndpoint.resolve("./health");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    log.info("Main server is running at {}, management server at {}", server.getURL(), managementEndpoint);
                    log.info("Health endpoint is available at {}", healthEndpoint);
                } else {
                    log.info("Server is running at {}", server.getURL());
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

    protected void shutdownHook(boolean logShutdown, Rethrow.RunnableChecked<Exception> run) {
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                if (logShutdown) {
                    log.warn("Shutdown signal received. Initiating graceful shutdown.");
                }
                try {
                    run.run();
                } catch (Exception e) {
                    log.error("Failed to complete graceful shutdown", e);
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
