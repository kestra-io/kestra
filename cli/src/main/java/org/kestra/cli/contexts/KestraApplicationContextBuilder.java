package org.kestra.cli.contexts;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.StringUtils;
import org.kestra.core.plugins.PluginRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Mostly on copy/paste of {@link ApplicationContextBuilder} in order to return a {@link KestraApplicationContext}
 * instead of {@link DefaultApplicationContext}
 */
public class KestraApplicationContextBuilder implements ApplicationContextConfiguration {
    private ClassLoader classLoader = getClass().getClassLoader();
    private List<String> environments = new ArrayList<>();
    private List<String> packages = new ArrayList<>();
    private Map<String, Object> properties = new LinkedHashMap<>();
    private PluginRegistry pluginRegistry;

    public KestraApplicationContextBuilder() {
        super();
    }

    public @Nonnull
    KestraApplicationContextBuilder classLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            this.classLoader = classLoader;
        }
        return this;
    }

    public @Nonnull
    KestraApplicationContextBuilder mainClass(Class mainClass) {
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

    public @Nonnull
    KestraApplicationContextBuilder packages(@Nullable String... packages) {
        if (packages != null) {
            this.packages.addAll(Arrays.asList(packages));
        }
        return this;
    }

    public @Nonnull
    KestraApplicationContextBuilder environments(@Nullable String... environments) {
        if (environments != null) {
            this.environments.addAll(Arrays.asList(environments));
        }
        return this;
    }

    public @Nonnull
    KestraApplicationContextBuilder properties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    public @Nonnull
    KestraApplicationContextBuilder pluginRegistry(@Nullable PluginRegistry pluginRegistry) {
        if (pluginRegistry != null) {
            this.pluginRegistry = pluginRegistry;
        }
        return this;
    }


    @SuppressWarnings("MagicNumber")
    public @Nonnull
    ApplicationContext build() {
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

    @Nonnull
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

    @Nonnull
    @Override
    public ConversionService<?> getConversionService() {
        return ConversionService.SHARED;
    }

    @Nonnull
    @Override
    public ClassPathResourceLoader getResourceLoader() {
        return ClassPathResourceLoader.defaultLoader(classLoader);
    }

    @Nonnull
    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }
}
