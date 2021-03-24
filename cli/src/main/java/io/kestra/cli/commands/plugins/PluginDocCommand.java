package io.kestra.cli.commands.plugins;

import com.google.common.base.Charsets;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.docs.DocumentationGenerator;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(
    name = "doc",
    description = "write documentation for all plugins currently installed"
)
public class PluginDocCommand extends AbstractCommand {
    @CommandLine.Parameters(index = "0", description = "Path to write documentations files")
    private Path output = Paths.get(System.getProperty("user.dir"), "docs");

    public PluginDocCommand() {
        super(false);
    }

    @CommandLine.Option(names = {"--core"}, description = "Also write core tasks docs files")
    private boolean core = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        PluginScanner pluginScanner = new PluginScanner(PluginDocCommand.class.getClassLoader());
        List<RegisteredPlugin> scan = new ArrayList<>(pluginScanner.scan(this.pluginsPath));

        if (core) {
            PluginScanner corePluginScanner = new PluginScanner(PluginDocCommand.class.getClassLoader());
            scan.add(corePluginScanner.scan());
        }

        for (RegisteredPlugin registeredPlugin : scan) {
            DocumentationGenerator
                .generate(registeredPlugin)
                .forEach(s -> {
                        File file = Paths.get(output.toAbsolutePath().toString(), s.getPath()).toFile();

                        if (!file.getParentFile().exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            file.getParentFile().mkdirs();
                        }

                        try {
                            com.google.common.io.Files
                                .asCharSink(
                                    file,
                                    Charsets.UTF_8
                                ).write(s.getBody());

                            stdOut("Generate doc in: {0}", file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                );
        }

        return 0;
    }
}
