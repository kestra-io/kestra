package io.kestra.cli.commands.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginManager;
import io.micronaut.http.uri.UriBuilder;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Provider;
import picocli.CommandLine;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

@CommandLine.Command(
    name = "install",
    description = "install a plugin"
)
public class PluginInstallCommand extends AbstractCommand {
    @CommandLine.Parameters(index = "0..*", description = "the plugins to install")
    List<String> dependencies = new ArrayList<>();

    @CommandLine.Option(names = {"--repositories"}, description = "url to additional maven repositories")
    private URI[] repositories;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private Provider<PluginManager> pluginManager;

    @Override
    public Integer call() throws Exception {
        super.call();

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

        List<PluginArtifact> pluginArtifacts;
        try {
           pluginArtifacts = dependencies.stream().map(PluginArtifact::of).toList();
        } catch (IllegalArgumentException e) {
            stdErr(e.getMessage());
            return CommandLine.ExitCode.USAGE;
        }
        PluginManager pluginManager = this.pluginManager.get();
        List<PluginArtifact> installed = pluginManager.install(
            pluginArtifacts,
            repositoryConfigs,
            false,
            pluginsPath
        );

        List<URI> uris = installed.stream().map(PluginArtifact::uri).toList();
        stdOut("Successfully installed plugins {0} into {1}", dependencies, uris);
        return CommandLine.ExitCode.OK;
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
