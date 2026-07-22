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
import io.kestra.core.models.flows.Flow;
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
 * <p>
 * Flow, task, trigger and dashboard schemas overlap heavily — they share most of
 * their nested plugin/property definitions. Generating each with {@code JsonSchemaGenerator}
 * independently would embed a full, near-duplicate {@code definitions} tree per type. Instead,
 * this command generates all four, then hoists every definition into one shared pool written
 * once as {@code definitions}, plus a small {@code roots} map of {@code SchemaType} name → the
 * {@code $ref} into that pool for that type's root class:
 * 
 * <pre>{@code
 * {
 *   "definitions": { "io.kestra.plugin.core.log.Log": {...}, ... },
 *   "roots": {
 *     "task": "#/definitions/io.kestra.core.models.tasks.Task",
 *     "trigger": "#/definitions/io.kestra.core.models.triggers.AbstractTrigger",
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p>
 * It is intended to be uploaded to GCS alongside {@code oss.json} at each release so that
 * running instances can fetch it via {@code kestra.plugins.schema-bundle-url-template} and offer
 * editor autocompletion for plugin types that are not yet locally installed (KIP-45).
 *
 * <p>
 * Run with a {@code --plugins} path pointing at the full-plugin set to capture every
 * available type.
 */
@CommandLine.Command(
    name = "plugins-schema",
    description = "Generate the per-release plugin schema bundle (flow, task, trigger, dashboard)",
    mixinStandardHelpOptions = true
)
@Slf4j
public class PluginsSchemaCommand extends AbstractCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** SchemaType → root class for schema generation (mirrors JsonSchemaCache). */
    private static final Map<SchemaType, Class<?>> SCHEMA_CLASSES = Map.of(
        SchemaType.FLOW, Flow.class,
        SchemaType.TASK, Task.class,
        SchemaType.TRIGGER, AbstractTrigger.class,
        SchemaType.DASHBOARD, Dashboard.class
    );

    @CommandLine.Option(names = { "-o", "--output" }, description = "Output file path", defaultValue = "plugins-schema.json")
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

        Map<String, Object> definitions = new LinkedHashMap<>();
        Map<String, Object> roots = new LinkedHashMap<>();
        for (Map.Entry<SchemaType, Class<?>> entry : SCHEMA_CLASSES.entrySet()) {
            SchemaType schemaType = entry.getKey();
            Class<?> cls = entry.getValue();
            try {
                Map<String, Object> schema = generator.schemas(cls);
                mergeDefinitions(definitions, schema);
                roots.put(schemaType.name().toLowerCase(), schema.get("$ref"));
                log.debug("Generated schema for type '{}'", schemaType);
            } catch (Exception e) {
                log.warn("Failed to generate schema for type '{}': {}", schemaType, e.getMessage());
            }
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("definitions", definitions);
        bundle.put("roots", roots);

        if (
            output.getParentFile() != null && !output.getParentFile().mkdirs()
                && !output.getParentFile().isDirectory()
        ) {
            throw new IOException("Failed to create output directory: " + output.getParentFile());
        }

        MAPPER.writeValue(output, bundle);
        stdOut("Plugin schema bundle written to {0} ({1} types, {2} shared definitions)", output.getAbsolutePath(), roots.size(), definitions.size());
        return 0;
    }

    /** Copies {@code schema}'s {@code definitions} into the shared pool, keeping the first (identical) copy of any class already hoisted from another root. */
    @SuppressWarnings("unchecked")
    private static void mergeDefinitions(Map<String, Object> sharedDefinitions, Map<String, Object> schema) {
        if (schema.get("definitions") instanceof Map<?, ?> definitions) {
            ((Map<String, Object>) definitions).forEach(sharedDefinitions::putIfAbsent);
        }
    }

    @Override
    protected boolean isPluginManagerEnabled() {
        return false;
    }
}
