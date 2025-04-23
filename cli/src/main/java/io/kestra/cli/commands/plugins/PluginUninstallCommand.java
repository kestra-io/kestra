package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.plugins.LocalPluginManager;
import io.kestra.core.plugins.MavenPluginDownloader;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginManager;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(
    name = "uninstall",
    description = "Uninstall plugins"
)
public class PluginUninstallCommand extends AbstractCommand {
    @Parameters(index = "0..*", description = "The plugins to uninstall. Represented as Maven artifact coordinates (i.e., <groupId>:<artifactId>:(<version>|LATEST)")
    List<String> dependencies = new ArrayList<>();

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    Provider<MavenPluginDownloader> mavenPluginRepositoryProvider;

    @Override
    public Integer call() throws Exception {
        super.call();

        List<PluginArtifact> pluginArtifacts;
        try {
            pluginArtifacts = dependencies.stream().map(PluginArtifact::fromCoordinates).toList();
        } catch (IllegalArgumentException e) {
            stdErr(e.getMessage());
            return CommandLine.ExitCode.USAGE;
        }

        final PluginManager pluginManager;

        // If a PLUGIN_PATH is provided, then use the LocalPluginManager
        if (pluginsPath != null) {
            pluginManager = new LocalPluginManager(mavenPluginRepositoryProvider.get());
        } else {
            // Otherwise, we delegate to the configured plugin-manager.
            pluginManager = this.pluginManagerProvider.get();
        }

        List<PluginArtifact> uninstalled = pluginManager.uninstall(
            pluginArtifacts,
            false,
            pluginsPath
        );

        List<URI> uris = uninstalled.stream().map(PluginArtifact::uri).toList();
        stdOut("Successfully uninstalled plugins {0} from {1}", dependencies, uris);
        return CommandLine.ExitCode.OK;
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
