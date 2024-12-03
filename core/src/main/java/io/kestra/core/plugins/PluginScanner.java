package io.kestra.core.plugins;

import io.kestra.core.app.AppBlockInterface;
import io.kestra.core.app.AppPluginInterface;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.secret.SecretPluginInterface;
import io.kestra.core.storages.StorageInterface;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@Slf4j
public class PluginScanner {
    ClassLoader parent;

    public PluginScanner(final ClassLoader parent) {
        this.parent = parent;
    }

    /**
     * Scans the specified top-level plugin directory for plugins.
     *
     * @param pluginPaths the absolute path to a top-level plugin directory.
     */
    public List<RegisteredPlugin> scan(final Path pluginPaths) {
        long start = System.currentTimeMillis();
        List<RegisteredPlugin> scanResult = new PluginResolver(pluginPaths)
            .resolves()
            .parallelStream()
            .map(plugin -> {
                log.debug("Loading plugins from path: {}", plugin.getLocation());

                final PluginClassLoader classLoader = PluginClassLoader.of(
                    plugin.getLocation(),
                    plugin.getResources(),
                    this.parent
                );

                log.debug(
                    "Scanning plugins from paths '{}' with classLoader '{}'",
                    Arrays.stream(plugin.getResources()).map(URL::getPath).collect(Collectors.joining("", "\n\t", "")),
                    classLoader
                );

                return scanClassLoader(classLoader, plugin, null);
            })
            .filter(RegisteredPlugin::isValid)
            .toList();

        int nbPlugins = scanResult.stream().mapToInt(registeredPlugin -> registeredPlugin.allClass().size()).sum();
        log.info("Registered {} plugins from {} groups (scan done in {}ms)", nbPlugins, scanResult.size(), System.currentTimeMillis() - start);
        return scanResult;
    }

    /**
     * Scans the main ClassLoader
     */
    public RegisteredPlugin scan() {
        try {
            long start = System.currentTimeMillis();
            Manifest manifest = new Manifest(IOUtils.toInputStream("Manifest-Version: 1.0\n" +
                "X-Kestra-Title: core\n" +
                "X-Kestra-Group: io.kestra.plugin.core\n",
                StandardCharsets.UTF_8
            ));

            RegisteredPlugin corePlugin = scanClassLoader(PluginScanner.class.getClassLoader(), null, manifest);
            log.info("Registered {} core plugins (scan done in {}ms)", corePlugin.allClass().size(), System.currentTimeMillis() - start);
            return corePlugin;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private RegisteredPlugin scanClassLoader(final ClassLoader classLoader,
                                             final ExternalPlugin externalPlugin,
                                             Manifest manifest) {
        List<Class<? extends Task>> tasks = new ArrayList<>();
        List<Class<? extends AbstractTrigger>> triggers = new ArrayList<>();
        List<Class<? extends Condition>> conditions = new ArrayList<>();
        List<Class<? extends StorageInterface>> storages = new ArrayList<>();
        List<Class<? extends SecretPluginInterface>> secrets = new ArrayList<>();
        List<Class<? extends TaskRunner>> taskRunners = new ArrayList<>();
        List<Class<? extends AppPluginInterface>> apps = new ArrayList<>();
        List<Class<? extends AppBlockInterface>> appBlocks = new ArrayList<>();
        List<Class<? extends Chart<?>>> charts = new ArrayList<>();
        List<Class<? extends DataFilter<?, ?>>> dataFilters = new ArrayList<>();
        List<String> guides = new ArrayList<>();
        Map<String, Class<?>> aliases = new HashMap<>();

        if (manifest == null) {
            manifest = getManifest(classLoader);
        }

        final ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, classLoader);
        try {
            for (Plugin plugin : sl) {
                if (plugin.getClass().isAnnotationPresent(Hidden.class)) {
                    continue;
                }

                switch (plugin) {
                    case Task task -> {
                        log.debug("Loading Task plugin: '{}'", plugin.getClass());
                        tasks.add(task.getClass());
                    }
                    case AbstractTrigger trigger -> {
                        log.debug("Loading Trigger plugin: '{}'", plugin.getClass());
                        triggers.add(trigger.getClass());
                    }
                    case Condition condition -> {
                        log.debug("Loading Condition plugin: '{}'", plugin.getClass());
                        conditions.add(condition.getClass());
                    }
                    case StorageInterface storage -> {
                        log.debug("Loading Storage plugin: '{}'", plugin.getClass());
                        storages.add(storage.getClass());
                    }
                    case SecretPluginInterface storage -> {
                        log.debug("Loading SecretPlugin plugin: '{}'", plugin.getClass());
                        secrets.add(storage.getClass());
                    }
                    case TaskRunner runner -> {
                        log.debug("Loading TaskRunner plugin: '{}'", plugin.getClass());
                        taskRunners.add(runner.getClass());
                    }
                    case AppPluginInterface app -> {
                        log.debug("Loading AppPlugin plugin: '{}'", plugin.getClass());
                        apps.add(app.getClass());
                    }
                    case AppBlockInterface appBlock -> {
                        log.debug("Loading AppBlocking plugin: '{}'", plugin.getClass());
                        appBlocks.add(appBlock.getClass());
                    }
                    case Chart<?> chart -> {
                        log.debug("Loading Chart plugin: '{}'", plugin.getClass());
                        //noinspection unchecked
                        charts.add((Class<? extends Chart<?>>) chart.getClass());
                    }
                    case DataFilter<?, ?> dataFilter -> {
                        log.debug("Loading DataFilter plugin: '{}'", plugin.getClass());
                        //noinspection unchecked
                        dataFilters.add((Class<? extends DataFilter<?, ?>>)  dataFilter.getClass());
                    }
                    default -> {
                    }
                }

                Plugin.getAliases(plugin.getClass()).forEach(alias -> aliases.put(alias, plugin.getClass()));
            }
        } catch (ServiceConfigurationError | NoClassDefFoundError e) {
            Object location = externalPlugin != null ? externalPlugin.getLocation() : "core";
            log.error("Unable to load all plugin classes from '{}'. Cause: [{}] {}",
                location,
                e.getClass().getSimpleName(),
                e.getMessage(),
                e
            );
        }

        var guidesDirectory = classLoader.getResource("doc/guides");
        if (guidesDirectory != null) {
            try (var fileSystem = FileSystems.newFileSystem(guidesDirectory.toURI(), Collections.emptyMap())) {
                var root = fileSystem.getPath("/doc/guides");
                try (var stream = Files.walk(root, 1)) {
                    stream
                        .skip(1) // first element is the root element
                        .sorted(Comparator.comparing(path -> path.getName(path.getParent().getNameCount()).toString()))
                        .forEach(guide -> {
                            var guideName = guide.getName(guide.getParent().getNameCount()).toString();
                            guides.add(guideName.substring(0, guideName.lastIndexOf('.')));
                        });
                }
            } catch (IOException | URISyntaxException e) {
                // silently fail
            }
        }

        return RegisteredPlugin.builder()
            .externalPlugin(externalPlugin)
            .manifest(manifest)
            .classLoader(classLoader)
            .tasks(tasks)
            .triggers(triggers)
            .conditions(conditions)
            .storages(storages)
            .secrets(secrets)
            .apps(apps)
            .appBlocks(appBlocks)
            .taskRunners(taskRunners)
            .charts(charts)
            .dataFilters(dataFilters)
            .guides(guides)
            .aliases(aliases.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().toLowerCase(),
                Function.identity()
            )))
            .build();
    }

    public static Manifest getManifest(ClassLoader classLoader) {
        try {
            URL url = classLoader.getResource(JarFile.MANIFEST_NAME);
            if (url != null) {
                return new Manifest(url.openStream());
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}
