package io.kestra.cli.schema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.preview.FileRenderer;

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
 * It is intended to be run by release CI against the full plugin catalog and embedded in the Kestra
 * JAR as the {@code /plugins-schema.json} classpath resource (via the {@code -PpluginsSchemaBundle}
 * Gradle property), so that every distribution offers editor autocompletion for plugin types that
 * are not yet locally installed without any network access. The bundle is not published
 * anywhere; {@code PluginSchemaBundleService} reads it straight off the classpath.
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

    /**
     * Extensions probed against {@code supports()} for renderers that declare none: file formats a
     * preview plausibly targets. Only a fallback — a renderer overriding
     * {@link FileRenderer#extensions()} is taken at its word, and an extension outside this list
     * has to be declared to be advertised.
     */
    private static final Set<String> PROBED_EXTENSIONS = Set.of(
        "avro", "parquet", "orc", "arrow", "feather",
        "csv", "tsv", "psv", "xlsx", "xls", "ods",
        "json", "jsonl", "ndjson", "ion", "xml", "yaml", "yml", "toml",
        "txt", "md", "log", "html", "pdf",
        "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp"
    );

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

        List<Map<String, Object>> plugins = catalogEntries(registry);
        Map<String, String> fileRenderers = fileRendererArtifacts(registry);

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("definitions", definitions);
        bundle.put("roots", roots);
        bundle.put("plugins", plugins);
        bundle.put("fileRenderers", fileRenderers);

        if (
            output.getParentFile() != null && !output.getParentFile().mkdirs()
                && !output.getParentFile().isDirectory()
        ) {
            throw new IOException("Failed to create output directory: " + output.getParentFile());
        }

        MAPPER.writeValue(output, bundle);
        stdOut(
            "Plugin schema bundle written to {0} ({1} types, {2} shared definitions, {3} catalog entries, {4} renderer extensions)",
            output.getAbsolutePath(),
            roots.size(),
            definitions.size(),
            plugins.size(),
            fileRenderers.size()
        );
        return 0;
    }

    /**
     * Builds the bundle's local catalog: one entry per scanned plugin jar, carrying its Maven
     * coordinates and its Java package group, so an instance can resolve a type FQCN to an
     * artifact for plugins the hosted catalog does not list (private or in-house ones).
     * Plugins whose coordinates cannot be read from the jar file name are skipped.
     */
    private static List<Map<String, Object>> catalogEntries(final PluginRegistry registry) {
        if (registry == null) {
            return List.of();
        }

        return registry.plugins().stream()
            .filter(plugin -> plugin.group() != null)
            .map(plugin -> artifactOf(plugin).map(artifact ->
            {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("title", plugin.title());
                entry.put("groupId", artifact.groupId());
                entry.put("artifactId", artifact.artifactId());
                entry.put("group", packageGroupOf(plugin));
                return entry;
            }))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * The package group backing type-to-artifact matching: the longest common package prefix of the
     * plugin's registered classes, since those packages are what flow {@code type} values are matched
     * against. The manifest's {@code X-Kestra-Group} is only a fallback — some plugins declare one
     * that differs from their real packages (e.g. plugin-transform-json declares
     * {@code io.kestra.plugin.json} while its types live under {@code io.kestra.plugin.transform.jsonata}).
     */
    static String packageGroupOf(final RegisteredPlugin plugin) {
        List<String> packages = plugin.allClass().stream()
            .map(Class::getPackageName)
            .distinct()
            .toList();
        return commonPackagePrefix(packages).orElse(plugin.group());
    }

    static Optional<String> commonPackagePrefix(final List<String> packages) {
        if (packages.isEmpty()) {
            return Optional.empty();
        }
        List<String> prefix = new ArrayList<>(List.of(packages.getFirst().split("\\.")));
        for (String pkg : packages) {
            List<String> segments = List.of(pkg.split("\\."));
            int common = 0;
            while (common < Math.min(prefix.size(), segments.size()) && prefix.get(common).equals(segments.get(common))) {
                common++;
            }
            prefix = prefix.subList(0, common);
        }
        return prefix.isEmpty() ? Optional.empty() : Optional.of(String.join(".", prefix));
    }

    /**
     * Builds the extension → {@code groupId:artifactId} map of every {@link FileRenderer} declared
     * by a scanned plugin, so the file preview can fetch the plugin that renders an extension no
     * installed renderer supports.
     */
    private static Map<String, String> fileRendererArtifacts(final PluginRegistry registry) {
        if (registry == null) {
            return Map.of();
        }

        Map<String, String> byExtension = new TreeMap<>();
        for (RegisteredPlugin plugin : registry.plugins()) {
            Optional<PluginArtifact> artifact = artifactOf(plugin);
            if (artifact.isEmpty()) {
                continue;
            }
            String coordinates = artifact.get().groupId() + ":" + artifact.get().artifactId();
            for (Class<? extends FileRenderer> renderer : plugin.getFileRenderers()) {
                declaredExtensions(renderer).forEach(
                    extension -> byExtension.putIfAbsent(extension.toLowerCase(Locale.ROOT), coordinates)
                );
            }
        }
        return byExtension;
    }

    // Core renderers ship with the distribution, so only external plugins carry installable coordinates.
    private static Optional<PluginArtifact> artifactOf(final RegisteredPlugin plugin) {
        if (plugin.getExternalPlugin() == null || plugin.getExternalPlugin().getLocation() == null) {
            return Optional.empty();
        }

        String fileName = new File(plugin.getExternalPlugin().getLocation().getPath()).getName();
        try {
            return Optional.of(PluginArtifact.fromFileName(fileName));
        } catch (IllegalArgumentException e) {
            log.debug("Skipping plugin '{}': cannot read Maven coordinates from file name '{}'.", plugin.group(), fileName);
            return Optional.empty();
        }
    }

    /**
     * Extensions a renderer handles: what it declares through {@link FileRenderer#extensions()},
     * falling back to probing {@link FileRenderer#supports(String)} over
     * {@link #PROBED_EXTENSIONS} for renderers that predate the declaration.
     */
    private static Set<String> declaredExtensions(final Class<? extends FileRenderer> renderer) {
        try {
            FileRenderer instance = renderer.getDeclaredConstructor().newInstance();
            Set<String> declared = instance.extensions();
            if (!declared.isEmpty()) {
                return declared;
            }
            return PROBED_EXTENSIONS.stream().filter(instance::supports).collect(Collectors.toSet());
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.debug("Could not read the extensions of renderer '{}'", renderer.getName(), e);
            return Set.of();
        }
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
