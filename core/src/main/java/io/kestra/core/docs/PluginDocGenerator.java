package io.kestra.core.docs;

import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a plugin's doc/schema files so they can be bundled in the plugin jar and served without
 * regenerating at runtime. Intended to be called from a plugin's build (e.g. a Gradle task) with the
 * plugin's own classpath (plugin + core + core-ee), so every referenced class resolves, including
 * Enterprise plugins when core-ee is present.
 */
@Slf4j
public final class PluginDocGenerator {
    private PluginDocGenerator() {
    }

    /**
     * Writes a {@code .md} and {@code .json} into {@code outDir} for each plugin class listed in the
     * {@code META-INF/services} file under {@code classesDir} (the file the annotation processor
     * generates), scanning the current classloader for their definitions.
     */
    public static void generate(Path classesDir, Path outDir) throws IOException {
        Set<String> pluginClasses = readPluginClasses(classesDir);
        if (pluginClasses.isEmpty()) {
            log.warn("No plugin classes found under {}, nothing to generate", classesDir);
            return;
        }
        Files.createDirectories(outDir);

        // Built directly rather than through a Micronaut context: it must run on a partial build
        // classpath where a full context may not start, and it only needs the JsonSchemaGenerator.
        PluginRegistry registry = DefaultPluginRegistry.getOrCreate();
        DocumentationGenerator generator = new DocumentationGenerator();
        generator.jsonSchemaGenerator = new JsonSchemaGenerator(registry);

        RegisteredPlugin scanned = new PluginScanner(PluginDocGenerator.class.getClassLoader()).scan();

        Iterable<Document> documents;
        try {
            documents = generator.generate(scanned);
        } catch (Exception e) {
            throw new IOException("Failed to generate plugin documentation", e);
        }

        int written = 0;
        for (Document doc : documents) {
            String fqcn = fqcnOf(doc.getPath());
            // the scan also sees core and dependency plugins; keep only this plugin's classes
            if (fqcn == null || !pluginClasses.contains(fqcn)) {
                continue;
            }
            Files.writeString(outDir.resolve(fqcn + ".md"), doc.getBody(), StandardCharsets.UTF_8);
            if (doc.getSchema() != null) {
                Files.write(outDir.resolve(fqcn + ".json"), JacksonMapper.ofJson().writeValueAsBytes(doc.getSchema()));
            }
            written++;
        }
        log.info("Generated docs for {} plugin class(es) into {}", written, outDir);
    }

    private static Set<String> readPluginClasses(Path classesDir) throws IOException {
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

    /** Document paths look like {@code <subgroup>/io.kestra.plugin.x.Foo}; pull the FQCN out. */
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
