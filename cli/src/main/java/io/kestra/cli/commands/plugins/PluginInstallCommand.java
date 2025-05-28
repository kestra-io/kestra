package io.kestra.cli.commands.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.LocalPluginManager;
import io.kestra.core.plugins.MavenPluginDownloader;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginCatalogService;
import io.kestra.core.plugins.PluginManager;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Provider;
import picocli.CommandLine;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "install",
    description = "Install plugins"
)
public class PluginInstallCommand extends AbstractCommand {

    @Option(names = {"--locally"}, description = "Specifies if plugins must be installed locally. If set to false the installation depends on your Kestra configuration.")
    boolean locally = true;

    @Option(names = {"--all"}, description = "Install all available plugins")
    boolean all = false;

    @Parameters(index = "0..*", description = "Plugins to install. Represented as Maven artifact coordinates (i.e., <groupId>:<artifactId>:(<version>|LATEST)")
    List<String> dependencies = new ArrayList<>();

    @Option(names = {"--repositories"}, description = "URL to additional Maven repositories")
    private URI[] repositories;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    Provider<MavenPluginDownloader> mavenPluginRepositoryProvider;

    @Inject
    Provider<PluginCatalogService> pluginCatalogService;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.locally && this.pluginsPath == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Missing required options '--plugins' " +
                "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        List<MavenPluginRepositoryConfig> repositoryConfigs = List.of();
        if (repositories != null) {
            repositoryConfigs = Arrays.stream(repositories)
                .map(uri -> {
                    MavenPluginRepositoryConfig.MavenPluginRepositoryConfigBuilder builder = MavenPluginRepositoryConfig
                        .builder()
                        .id(IdUtils.create());

                    String userInfo = uri.getUserInfo();
                    if (userInfo != null) {
                        String[] userInfoParts = userInfo.split(":");
                        builder = builder.basicAuth(new MavenPluginRepositoryConfig.BasicAuth(
                            userInfoParts[0],
                            userInfoParts[1]
                        ));
                    }
                    builder.url(UriBuilder.of(uri).userInfo(null).build().toString());
                    return builder.build();
                }).toList();
        }

        if (all) {
            PluginCatalogService service = pluginCatalogService.get();
            dependencies = service.get().stream().map(Objects::toString).toList();
        }

        if (dependencies.isEmpty()) {
            stdErr("Error: No plugin to install.");
            return CommandLine.ExitCode.OK;
        }

        final List<PluginArtifact> pluginArtifacts;
        try {
           pluginArtifacts = dependencies.stream().map(PluginArtifact::fromCoordinates).toList();
        } catch (IllegalArgumentException e) {
            stdErr(e.getMessage());
            return CommandLine.ExitCode.USAGE;
        }

        try (final PluginManager pluginManager = getPluginManager()) {

            List<PluginArtifact> installed;
            if (all) {
                installed = new ArrayList<>(pluginArtifacts.size());
                for (PluginArtifact pluginArtifact : pluginArtifacts) {
                    try {
                        installed.add(pluginManager.install(pluginArtifact, repositoryConfigs, false, pluginsPath));
                    } catch (KestraRuntimeException e) {
                        String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        stdErr("Failed to install plugin {0}. Cause: {1}", pluginArtifact, cause);
                    }
                }
            } else {
                installed = pluginManager.install(pluginArtifacts, repositoryConfigs, false, pluginsPath);
            }

            List<URI> uris = installed.stream().map(PluginArtifact::uri).toList();
            stdOut("Successfully installed plugins {0} into {1}", dependencies, uris);
            return CommandLine.ExitCode.OK;
        }
    }

    private PluginManager getPluginManager() {
        return locally ? new LocalPluginManager(mavenPluginRepositoryProvider.get()) : this.pluginManagerProvider.get();
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
