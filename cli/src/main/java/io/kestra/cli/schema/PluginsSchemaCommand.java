package io.kestra.cli.schema;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.docs.SchemaType;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * CLI command that generates the per-release plugin schema bundle.
 *
 * <p>The bundle is a single JSON file keyed by {@link SchemaType} name, containing a full
 * Draft-7 JSON Schema for each type (task, trigger, plugindefault, dashboard). It is intended
 * to be uploaded to GCS alongside {@code oss.json} at each release so that running instances
 * can fetch it via {@code kestra.plugins.schema-bundle-url-template} and offer editor
 * autocompletion for plugin types that are not yet locally installed (KIP-45).
 *
 * <p>Run with a {@code --plugins} path pointing at the full-plugin set to capture every
 * available type.
 */
@CommandLine.Command(
    name = "plugins-schema",
    description = "Generate the per-release plugin schema bundle (task, trigger, plugindefault, dashboard)",
    mixinStandardHelpOptions = true
)
@Slf4j
public class PluginsSchemaCommand extends AbstractCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** SchemaType → root class for schema generation (mirrors JsonSchemaCache). */
    private static final Map<SchemaType, Class<?>> SCHEMA_CLASSES = Map.of(
        SchemaType.TASK, Task.class,
        SchemaType.TRIGGER, AbstractTrigger.class,
        SchemaType.PLUGINDEFAULT, PluginDefault.class,
        SchemaType.DASHBOARD, Dashboard.class
    );

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file path", defaultValue = "plugins-schema.json")
    private File output;

    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Override
    public Integer call() throws Exception {
        super.call();

        PluginRegistry registry = pluginRegistry;
        if (registry == null && pluginsPath != null) {
            registry = DefaultPluginRegistry.getOrCreate();
            registry.registerIfAbsent(pluginsPath);
        }

        if (registry == null || registry.plugins().isEmpty()) {
            log.warn("No plugins loaded — bundle will cover core types only. Pass --plugins to include installed plugins.");
        } else {
            log.info("Generating plugin schema bundle for {} registered plugin(s).", registry.plugins().size());
        }

        JsonSchemaGenerator generator = jsonSchemaGenerator != null
            ? jsonSchemaGenerator
            : new JsonSchemaGenerator(registry != null ? registry : DefaultPluginRegistry.getOrCreate());

        Map<String, Object> bundle = new LinkedHashMap<>();
        for (Map.Entry<SchemaType, Class<?>> entry : SCHEMA_CLASSES.entrySet()) {
            SchemaType schemaType = entry.getKey();
            Class<?> cls = entry.getValue();
            try {
                Map<String, Object> schema = generator.schemas(cls);
                bundle.put(schemaType.name().toLowerCase(), schema);
                log.debug("Generated schema for type '{}'", schemaType);
            } catch (Exception e) {
                log.warn("Failed to generate schema for type '{}': {}", schemaType, e.getMessage());
            }
        }

        if (output.getParentFile() != null && !output.getParentFile().mkdirs()
            && !output.getParentFile().isDirectory()) {
            throw new IOException("Failed to create output directory: " + output.getParentFile());
        }

        MAPPER.writeValue(output, bundle);
        stdOut("Plugin schema bundle written to {0} ({1} types)", output.getAbsolutePath(), bundle.size());
        return 0;
    }

    @Override
    protected boolean isPluginManagerEnabled() {
        return false;
    }
}
