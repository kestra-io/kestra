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
import java.util.Set;

/**
 * Generates plugin doc/schema from the classpath and writes them as files. Meant to run at plugin
 * build time with the plugin's own classpath (plugin + core + core-ee) so docs ship in the jar.
 */
public final class PluginDocGenerator {
    private PluginDocGenerator() {
    }

    // args[0] = compiled classes dir, args[1] = output dir
    public static void main(String[] args) throws Exception {
        Path classesDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);

        Set<String> pluginClasses = readPluginClasses(classesDir);
        if (pluginClasses.isEmpty()) {
            return;
        }
        Files.createDirectories(outDir);

        // Built directly, not via Micronaut: a context may not start on a partial build classpath.
        PluginRegistry registry = DefaultPluginRegistry.getOrCreate();
        DocumentationGenerator generator = new DocumentationGenerator();
        generator.jsonSchemaGenerator = new JsonSchemaGenerator(registry);

        RegisteredPlugin scanned = new PluginScanner(PluginDocGenerator.class.getClassLoader()).scan();

        for (Document doc : generator.generate(scanned)) {
            String fqcn = fqcnOf(doc.getPath());
            // the scan also sees core + dependency plugins; keep only this plugin's classes
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
