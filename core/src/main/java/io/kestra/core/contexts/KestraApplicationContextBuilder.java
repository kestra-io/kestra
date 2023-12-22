package io.kestra.core.contexts;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.StringUtils;
import io.kestra.core.plugins.PluginRegistry;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.util.*;

/**
 * Mostly on copy/paste of {@link ApplicationContextBuilder} in order to return a {@link KestraApplicationContext}
 * instead of {@link DefaultApplicationContext}
 */
public class KestraApplicationContextBuilder implements ApplicationContextConfiguration {
    private ClassLoader classLoader = getClass().getClassLoader();
    private final List<String> environments = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private PluginRegistry pluginRegistry;

    public KestraApplicationContextBuilder() {
        super();
    }

    public @NonNull KestraApplicationContextBuilder classLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            this.classLoader = classLoader;
        }
        return this;
    }

    public @NonNull KestraApplicationContextBuilder mainClass(Class<?> mainClass) {
        if (mainClass != null) {
            if (this.classLoader == null) {
                this.classLoader = mainClass.getClassLoader();
            }
            String name = mainClass.getPackage().getName();
            if (StringUtils.isNotEmpty(name)) {
                packages(name);
            }
        }
        return this;
    }

    public @NonNull KestraApplicationContextBuilder packages(@Nullable String... packages) {
        if (packages != null) {
            this.packages.addAll(Arrays.asList(packages));
        }
        return this;
    }

    public @NonNull KestraApplicationContextBuilder environments(@Nullable String... environments) {
        if (environments != null) {
            this.environments.addAll(Arrays.asList(environments));
        }
        return this;
    }

    public @NonNull KestraApplicationContextBuilder properties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    public @NonNull KestraApplicationContextBuilder pluginRegistry(@Nullable PluginRegistry pluginRegistry) {
        if (pluginRegistry != null) {
            this.pluginRegistry = pluginRegistry;
        }
        return this;
    }

    @SuppressWarnings("MagicNumber")
    public @NonNull ApplicationContext build() {
        DefaultApplicationContext applicationContext = new KestraApplicationContext(this, this.pluginRegistry);
        Environment environment = applicationContext.getEnvironment();

        if (!packages.isEmpty()) {
            for (String aPackage : packages) {
                environment.addPackage(aPackage);
            }
        }

        if (!properties.isEmpty()) {
            PropertySource contextProperties = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
            environment.addPropertySource(contextProperties);
        }

        return applicationContext;
    }

    @NonNull
    @Override
    public List<String> getEnvironments() {
        return this.environments;
    }

    @Override
    public Optional<Boolean> getDeduceEnvironments() {
        return Optional.empty();
    }

    @Override
    public boolean isEnvironmentPropertySource() {
        return true;
    }

    @Nullable
    @Override
    public List<String> getEnvironmentVariableIncludes() {
        return null;
    }

    @Nullable
    @Override
    public List<String> getEnvironmentVariableExcludes() {
        return null;
    }

    @Override
    public Optional<MutableConversionService> getConversionService() {
        return Optional.of((MutableConversionService) ConversionService.SHARED);
    }

    @NonNull
    @Override
    public ClassPathResourceLoader getResourceLoader() {
        return ClassPathResourceLoader.defaultLoader(classLoader);
    }

    @NonNull
    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }
}
