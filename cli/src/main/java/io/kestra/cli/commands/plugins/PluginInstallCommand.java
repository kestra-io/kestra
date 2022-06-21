package io.kestra.cli.commands.plugins;

import org.apache.commons.io.FilenameUtils;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.plugins.PluginDownloader;
import io.kestra.cli.plugins.RepositoryConfig;
import io.kestra.core.utils.IdUtils;
import picocli.CommandLine;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private PluginDownloader pluginDownloader;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.pluginsPath == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Missing required options '--plugins' " +
                "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        if (!pluginsPath.toFile().exists()) {
            if (!pluginsPath.toFile().mkdir()) {
                throw new RuntimeException("Cannot create directory: " + pluginsPath.toFile().getAbsolutePath());
            }
        }

        if (repositories != null) {
            Arrays.stream(repositories)
                .forEach(s -> pluginDownloader.addRepository(new RepositoryConfig(IdUtils.create(), "default", s.toString())));
        }

        List<URL> resolveUrl = pluginDownloader.resolve(dependencies);
        stdOut("Resolved Plugin(s) with {0}", resolveUrl);

        for (URL url: resolveUrl) {
            Files.copy(
                Paths.get(url.toURI()),
                Paths.get(pluginsPath.toString(), FilenameUtils.getName(url.toString())),
                StandardCopyOption.REPLACE_EXISTING
            );
        }

        stdOut("Successfully installed plugins {0} into {1}", dependencies, pluginsPath);

        return 0;
    }
}
