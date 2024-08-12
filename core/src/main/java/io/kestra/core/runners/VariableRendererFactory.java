package io.kestra.core.runners;

import io.kestra.core.expression.PebbleExtension;
import io.kestra.core.expression.PebbleFilter;
import io.kestra.core.expression.PebbleFunction;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.runners.pebble.ExtensionCustomizer;
import io.kestra.core.runners.pebble.PebbleLruCache;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Classes;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.extension.Function;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Factory
public class VariableRendererFactory {

    private static final Logger log = LoggerFactory.getLogger(VariableRendererFactory.class);

    @Inject
    VariableRenderer.VariableConfiguration configuration;

    @Inject
    List<AbstractExtension> extensions;

    @Inject
    PluginRegistry pluginRegistry;

    @Singleton
    @Requires(missingBeans = VariableRenderer.class)
    public VariableRenderer variableRenderer(PebbleEngine pebbleEngine) {
        return new VariableRenderer(pebbleEngine, configuration.recursiveRendering());
    }

    @Singleton
    public PebbleEngine pebbleEngine() {
        PebbleEngine.Builder builder = new PebbleEngine.Builder()
            .registerExtensionCustomizer(ExtensionCustomizer::new)
            .strictVariables(true)
            .cacheActive(configuration.cacheEnabled())
            .newLineTrimming(false)
            .autoEscaping(false);

        // register core extensions
        extensions.forEach(builder::extension);

        // register custom extensions
        Map<Class<?>, List<PebbleExtension>> extensions = allPebbleExtensionClasses(pluginRegistry)
            .map(Classes::newInstance)
            .collect(Collectors.groupingBy(it -> {
                if (it instanceof PebbleFunction) {
                    return PebbleFunction.class;
                }
                if (it instanceof PebbleFilter) {
                    return PebbleFilter.class;
                }
                return Object.class; // will be silently ignored
            }));

        Map<String, Filter> filters = loadCustomPebbleFilters(extensions);
        Map<String, Function> functions = loadCustomPebbleFunctions(extensions);

        builder.extension(new AbstractExtension() {
            @Override
            public Map<String, Filter> getFilters() {
                return filters;
            }

            @Override
            public Map<String, Function> getFunctions() {
                return functions;
            }
        });

        if (configuration.cacheEnabled()) {
            builder.templateCache(new PebbleLruCache(configuration.cacheSize()));
        }

        return builder.build();
    }

    private static Map<String, Function> loadCustomPebbleFunctions(Map<Class<?>, List<PebbleExtension>> extensions) {
        return extensions.getOrDefault(PebbleFunction.class, List.of()).stream()
            .map(it -> (PebbleFunction) it)
            .filter(Objects::nonNull)
            .peek(it -> {
                if (log.isDebugEnabled()) {
                    log.debug("Registered PebbleFunction for name '{}': {}", it.name(), it.getClass());
                }
            })
            .collect(Collectors.toMap(PebbleFunction::name, it -> it, (existing, duplicate) -> {
                log.warn("Duplicate PebbleFunction detected for name: {}", ((PebbleFunction)existing).name());
                return existing;
            }));
    }

    private static Map<String, Filter> loadCustomPebbleFilters(Map<Class<?>, List<PebbleExtension>> extensions) {
        return extensions.getOrDefault(PebbleFilter.class, List.of()).stream()
            .map(it -> (PebbleFilter) it)
            .filter(Objects::nonNull)
            .peek(it -> {
                if (log.isDebugEnabled()) {
                    log.debug("Registered PebbleFilter for name '{}': {}", it.name(), it.getClass());
                }
            })
            .collect(Collectors.toMap(PebbleFilter::name, it -> it, (existing, duplicate) -> {
                log.warn("Duplicate PebbleFilter plugin detected for name: {}", ((PebbleFilter)existing).name());
                return existing;
            }));
    }

    /**
     * @return all plugin classes for the {@link StorageInterface}s.
     */
    private static Stream<Class<? extends PebbleExtension>> allPebbleExtensionClasses(final PluginRegistry pluginRegistry) {
        return pluginRegistry.plugins()
            .stream()
            .map(RegisteredPlugin::getPebbleExtensions)
            .flatMap(List::stream);
    }
}
