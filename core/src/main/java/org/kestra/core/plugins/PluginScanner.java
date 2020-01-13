package org.kestra.core.plugins;

import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.annotation.Controller;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.listeners.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.storages.StorageInterface;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public class PluginScanner {
    /**
     * Scans the specified top-level plugin directory for plugins.
     *
     * @param pluginPaths the absolute path to a top-level plugin directory.
     */
    public List<RegisteredPlugin> scan(final Path pluginPaths) {
        return new PluginResolver(pluginPaths)
            .resolves()
            .stream()
            .map(plugin -> {
                log.debug("Loading plugins from path: {}", plugin.getLocation());

                final PluginClassLoader classLoader = PluginClassLoader.of(
                    plugin.getLocation(),
                    plugin.getResources(),
                    PluginScanner.class.getClassLoader()
                );

                log.debug("Initialized new ClassLoader: {}", classLoader);

                return scanUrlsForPlugins(plugin, classLoader);
            })
            .filter(RegisteredPlugin::isValid)
            .collect(Collectors.toList());

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RegisteredPlugin scanUrlsForPlugins(ExternalPlugin plugin, final ClassLoader classLoader) {
        log.debug(
            "Scanning plugins from paths: {}",
            Arrays.stream(plugin.getResources()).map(URL::getPath).collect(Collectors.joining("", "\n\t", ""))
        );

        final SoftServiceLoader<BeanIntrospectionReference> definitions = SoftServiceLoader.load(
            BeanIntrospectionReference.class,
            classLoader
        );

        List<Class<? extends Task>> tasks = new ArrayList<>();
        List<Class<? extends Condition>> conditions = new ArrayList<>();
        List<Class<? extends StorageInterface>> storages = new ArrayList<>();
        List<Class<?>> controllers = new ArrayList<>();

        for (ServiceDefinition<BeanIntrospectionReference> definition : definitions) {
            if (definition.isPresent()) {
                final BeanIntrospectionReference ref = definition.load();

                if (Task.class.isAssignableFrom(ref.getBeanType())) {
                    tasks.add(ref.getBeanType());
                }

                if (Condition.class.isAssignableFrom(ref.getBeanType())) {
                    conditions.add(ref.getBeanType());
                }

                if (StorageInterface.class.isAssignableFrom(ref.getBeanType())) {
                    storages.add(ref.getBeanType());
                }

                if (ref.getBeanType().isAnnotationPresent(Controller.class)) {
                    controllers.add(ref.getBeanType());
                }
            }
        }

        return RegisteredPlugin.builder()
            .externalPlugin(plugin)
            .classLoader(classLoader)
            .tasks(tasks)
            .conditions(conditions)
            .controllers(controllers)
            .storages(storages)
            .build();
    }
}
