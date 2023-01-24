package io.kestra.core;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import io.kestra.core.contexts.KestraApplicationContextBuilder;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Helpers {
    public static long FLOWS_COUNT = 52;

    public static ApplicationContext applicationContext() throws URISyntaxException {
        return applicationContext(
            pluginsPath()
        );
    }

    public static ApplicationContext applicationContext(Map<String, Object> properties) throws URISyntaxException {
        return applicationContext(
            pluginsPath(),
            properties,
            new String[]{Environment.TEST}
        );
    }

    public static ApplicationContext applicationContext(Path pluginsPath) {
        return applicationContext(
            pluginsPath,
            null,
            new String[]{Environment.TEST}
        );
    }


    private static Path pluginsPath() throws URISyntaxException {
        return Paths.get(Objects.requireNonNull(Helpers.class.getClassLoader().getResource("plugins")).toURI());
    }

    private static ApplicationContext applicationContext(Path pluginsPath, Map<String, Object> properties, String[] envs) {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(Thread.currentThread().getContextClassLoader());
        }

        PluginScanner pluginScanner = new PluginScanner(KestraClassLoader.instance());
        List<RegisteredPlugin> scan = pluginScanner.scan(pluginsPath);
        PluginRegistry pluginRegistry = new PluginRegistry(scan);
        KestraClassLoader.instance().setPluginRegistry(pluginRegistry);

        return new KestraApplicationContextBuilder()
            .mainClass(Helpers.class)
            .environments(envs)
            .properties(properties)
            .classLoader(KestraClassLoader.instance())
            .pluginRegistry(pluginRegistry)
            .build();
    }

    public static void runApplicationContext(Consumer<ApplicationContext> consumer) throws URISyntaxException {
        try (ApplicationContext applicationContext = Helpers.applicationContext().start()) {
            consumer.accept(applicationContext);
        }
    }

    public static void runApplicationContext(BiConsumer<ApplicationContext, EmbeddedServer> consumer) throws URISyntaxException {
        try (ApplicationContext applicationContext = Helpers.applicationContext().start()) {
            EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
            embeddedServer.start();

            consumer.accept(applicationContext, embeddedServer);
        }
    }

    public static void runApplicationContext(String[] env, BiConsumer<ApplicationContext, EmbeddedServer> consumer) throws URISyntaxException {
        try (ApplicationContext applicationContext = applicationContext(
            pluginsPath(),
            null,
            env
        ).start()) {
            EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
            embeddedServer.start();

            consumer.accept(applicationContext, embeddedServer);
        }
    }
}
