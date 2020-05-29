package org.kestra.cli.commands.plugins;

import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.plugins.PluginDownloader;
import picocli.CommandLine;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(
    name = "install",
    description = "install a plugin"
)
@Slf4j
public class PluginInstallCommand extends AbstractCommand {
    @CommandLine.Parameters(index = "0..*", description = "the plugins to install")
    List<String> dependencies = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    PluginDownloader pluginDownloader;

    public PluginInstallCommand() {
        super(false);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void run() {
        super.run();

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

        try {
            List<URL> resolveUrl = pluginDownloader.resolve(dependencies);
            log.debug("Resolved Plugin(s) with {}", resolveUrl);

            for (URL url: resolveUrl) {
                Files.copy(
                    Paths.get(url.toURI()),
                    Paths.get(pluginsPath.toString(), FilenameUtils.getName(url.toString())),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

            log.info("Successfully installed plugins {} into {}", dependencies, pluginsPath);
        } catch (DependencyResolutionException | URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
