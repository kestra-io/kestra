package io.kestra.core.docs;

import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates doc/schema for the plugin(s) on the classpath and writes them as files, intended to be
 * run at plugin build time (e.g. by a Gradle task) with the plugin's own classpath so the docs can
 * be bundled in the jar and served without regenerating at runtime.
 * <p>
 * It runs on the build classpath (plugin + core + its dependencies on one classloader), so any
 * plugin's referenced classes resolve, including Enterprise plugins when core-ee is present. The
 * classpath also carries core and dependency plugins, so only this plugin's classes are kept,
 * identified from the {@code META-INF/services} file the annotation processor generates.
 * <p>
 * The generator is built directly rather than through a Micronaut context: it must run on a partial
 * build classpath where a full context may not start, and its only dependency is the
 * {@link JsonSchemaGenerator}, which only needs a {@link PluginRegistry}.
 */
public final class PluginDocGenerator {
    private PluginDocGenerator() {
    }

    /**
     * @param args [0] compiled classes dir (e.g. build/classes/java/main), [1] output dir
     */
    public static void main(String[] args) throws Exception {
        Path classesDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);

        Set<String> pluginClasses = readPluginClasses(classesDir);
        if (pluginClasses.isEmpty()) {
            return;
        }
        Files.createDirectories(outDir);

        PluginRegistry registry = DefaultPluginRegistry.getOrCreate();
        DocumentationGenerator generator = new DocumentationGenerator();
        generator.jsonSchemaGenerator = new JsonSchemaGenerator(registry);

        RegisteredPlugin scanned = new PluginScanner(PluginDocGenerator.class.getClassLoader()).scan();

        for (Document doc : generator.generate(scanned)) {
            String fqcn = fqcnOf(doc.getPath());
            if (fqcn == null || !pluginClasses.contains(fqcn)) {
                continue;
            }
            Files.writeString(outDir.resolve(fqcn + ".md"), doc.getBody(), StandardCharsets.UTF_8);
            if (doc.getSchema() != null) {
                Files.write(outDir.resolve(fqcn + ".json"), JacksonMapper.ofJson().writeValueAsBytes(doc.getSchema()));
            }
        }
    }

    private static Set<String> readPluginClasses(Path classesDir) throws Exception {
        Set<String> classes = new HashSet<>();
        Path serviceFile = classesDir.resolve("META-INF/services/io.kestra.core.models.Plugin");
        if (Files.exists(serviceFile)) {
            for (String line : Files.readAllLines(serviceFile)) {
                String fqcn = line.trim();
                if (!fqcn.isEmpty() && !fqcn.startsWith("#")) {
                    classes.add(fqcn);
                }
            }
        }
        return classes;
    }

    /** Document paths look like {@code <plugin>/tasks/io.kestra.plugin.x.Foo.md}; pull the FQCN out. */
    private static String fqcnOf(String path) {
        if (path == null) {
            return null;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String fqcn = name.substring(0, dot);
        return fqcn.contains(".") ? fqcn : null;
    }
}
