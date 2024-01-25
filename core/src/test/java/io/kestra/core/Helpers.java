package io.kestra.core;

import io.kestra.core.contexts.KestraApplicationContextBuilder;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.PluginScanner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Helpers {
    public static final long FLOWS_COUNT = countFlows();

    private static int countFlows() {
        int count = 0;
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream("flows/valids/");
             var br = new BufferedReader(new InputStreamReader(in))) {
            while (br.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count;
    }

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
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginScanner.continuousScan(pluginsPath, pluginRegistry);
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

    public static void runApplicationContext(String[] env, Map<String, Object> properties, BiConsumer<ApplicationContext, EmbeddedServer> consumer) throws URISyntaxException {
        try (ApplicationContext applicationContext = applicationContext(
            pluginsPath(),
            properties,
            env
        ).start()) {
            EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
            embeddedServer.start();

            consumer.accept(applicationContext, embeddedServer);
        }
    }

    public static void runApplicationContext(String[] env, BiConsumer<ApplicationContext, EmbeddedServer> consumer) throws URISyntaxException {
        runApplicationContext(env, null, consumer);
    }
}
