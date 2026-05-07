package io.kestra.cli.schema;

import java.io.File;
import java.io.IOException;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "configuration-schema",
    description = "Generate JSON Schema for Kestra configuration properties and installed plugin storages",
    mixinStandardHelpOptions = true
)
@Slf4j
public class ConfigurationSchemaCommand extends AbstractCommand {

    @CommandLine.Option(names = { "-o", "--output" }, description = "Output file path", defaultValue = "configuration-schema.json")
    private File output;

    @Override
    public Integer call() throws Exception {
        super.call();

        PluginRegistry registry = pluginRegistry;
        if (registry == null && pluginsPath != null) {
            registry = DefaultPluginRegistry.getOrCreate();
            registry.registerIfAbsent(pluginsPath);
        }

        if (registry == null) {
            log.warn("No plugins loaded (no --plugins path provided). Storage plugin schemas will be skipped.");
        }

        var generator = new ConfigurationSchemaGenerator();
        var schema = generator.generate(registry);

        if (output.getParentFile() != null && !output.getParentFile().mkdirs()
            && !output.getParentFile().isDirectory()) {
            throw new IOException("Failed to create output directory: " + output.getParentFile());
        }
        ConfigurationSchemaGenerator.write(schema, output);

        stdOut("Configuration schema written to {0}", output.getAbsolutePath());
        return 0;
    }

    @Override
    protected boolean isPluginManagerEnabled() {
        return false;
    }
}
