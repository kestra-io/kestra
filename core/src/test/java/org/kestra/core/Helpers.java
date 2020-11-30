package org.kestra.core;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.kestra.core.contexts.KestraApplicationContextBuilder;
import org.kestra.core.contexts.KestraClassLoader;
import org.kestra.core.plugins.PluginRegistry;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class Helpers {
    public static long FLOWS_COUNT = 38;

    public static ApplicationContext applicationContext() throws URISyntaxException {
        return applicationContext(Paths.get(Objects.requireNonNull(Helpers.class.getClassLoader().getResource("plugins")).toURI()));
    }

    public static ApplicationContext applicationContext(Path pluginsPath) {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(Thread.currentThread().getContextClassLoader());
        }

        PluginScanner pluginScanner = new PluginScanner(KestraClassLoader.instance());
        List<RegisteredPlugin> scan = pluginScanner.scan(pluginsPath);

        return new KestraApplicationContextBuilder()
            .mainClass(Helpers.class)
            .environments(Environment.TEST)
            .classLoader(KestraClassLoader.instance())
            .pluginRegistry(new PluginRegistry(scan))
            .build();
    }

    public static void runApplicationContext(BiConsumer<ApplicationContext, EmbeddedServer> consumer) throws URISyntaxException {
        try (ApplicationContext applicationContext = Helpers.applicationContext().start()) {
            EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
            embeddedServer.start();

            consumer.accept(applicationContext, embeddedServer);
        }
    }
}
