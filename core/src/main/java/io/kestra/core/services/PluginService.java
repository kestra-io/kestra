package io.kestra.core.services;

import io.kestra.core.contexts.KestraApplicationContext;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.micronaut.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PluginService {
    @Inject
    ApplicationContext applicationContext;

    public List<RegisteredPlugin> allPlugins() {
        if (!(applicationContext instanceof KestraApplicationContext)) {
            throw new RuntimeException("Invalid ApplicationContext");
        }

        KestraApplicationContext context = (KestraApplicationContext) applicationContext;
        PluginRegistry pluginRegistry = context.getPluginRegistry();

        List<RegisteredPlugin> plugins = new ArrayList<>();
        if (pluginRegistry != null) {
            plugins = new ArrayList<>(pluginRegistry.getPlugins());
        }

        PluginScanner corePluginScanner = new PluginScanner(PluginService.class.getClassLoader());
        plugins.add(corePluginScanner.scan());

        return plugins;
    }
}
