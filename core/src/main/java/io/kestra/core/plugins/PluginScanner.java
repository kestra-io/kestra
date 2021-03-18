package io.kestra.core.plugins;

import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.annotation.Controller;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.storages.StorageInterface;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@Slf4j
public class PluginScanner {
    ClassLoader parent;

    public PluginScanner(ClassLoader parent) {
        this.parent = parent;
    }

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
            .collect(Collectors.toList());
    }

    /**
     * Scans the main ClassLoader
     */
    public RegisteredPlugin scan() {
        try {
            Manifest manifest = new Manifest(IOUtils.toInputStream("Manifest-Version: 1.0\n" +
                "X-Kestra-Title: core\n" +
                "X-Kestra-Group: io.kestra.core.tasks\n",
                StandardCharsets.UTF_8
            ));

            return scanClassLoader(PluginScanner.class.getClassLoader(), null, manifest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RegisteredPlugin scanClassLoader(final ClassLoader classLoader, ExternalPlugin externalPlugin, Manifest manifest) {
        List<Class<? extends Task>> tasks = new ArrayList<>();
        List<Class<? extends AbstractTrigger>> triggers = new ArrayList<>();
        List<Class<? extends Condition>> conditions = new ArrayList<>();
        List<Class<? extends StorageInterface>> storages = new ArrayList<>();
        List<Class<?>> controllers = new ArrayList<>();

        final SoftServiceLoader<BeanIntrospectionReference> definitions = SoftServiceLoader.load(
            BeanIntrospectionReference.class,
            classLoader
        );

        if (manifest == null) {
            manifest = getManifest(classLoader);
        }

        for (ServiceDefinition<BeanIntrospectionReference> definition : definitions) {
            if (definition.isPresent()) {
                final BeanIntrospectionReference ref = definition.load();
                Class beanType = ref.getBeanType();

                if (Modifier.isAbstract(beanType.getModifiers())) {
                    continue;
                }

                if (Task.class.isAssignableFrom(beanType)) {
                    tasks.add(beanType);
                }

                if (AbstractTrigger.class.isAssignableFrom(beanType)) {
                    triggers.add(beanType);
                }

                if (Condition.class.isAssignableFrom(beanType)) {
                    conditions.add(beanType);
                }

                if (StorageInterface.class.isAssignableFrom(beanType)) {
                    storages.add(beanType);
                }

                if (beanType.isAnnotationPresent(Controller.class)) {
                    controllers.add(beanType);
                }
            }
        }

        return RegisteredPlugin.builder()
            .externalPlugin(externalPlugin)
            .manifest(manifest)
            .classLoader(classLoader)
            .tasks(tasks)
            .triggers(triggers)
            .conditions(conditions)
            .controllers(controllers)
            .storages(storages)
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
