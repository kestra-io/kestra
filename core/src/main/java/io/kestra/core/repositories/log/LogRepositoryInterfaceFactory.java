package io.kestra.core.repositories.log;

import io.kestra.core.plugins.AbstractPluginInterfaceFactory;
import io.kestra.core.plugins.ApplicationContextInitializable;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.LogRepositoryInterface;

import io.micronaut.context.ApplicationContext;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Map;

/**
 * Factory for constructing {@link LogRepositoryInterface} objects from configuration.
 * <p>
 * The backend is selected by {@code kestra.logs.type} and discovered as a plugin via the
 * {@link PluginRegistry}; the shared mechanism lives in {@link AbstractPluginInterfaceFactory}.
 */
public class LogRepositoryInterfaceFactory extends AbstractPluginInterfaceFactory<LogRepositoryInterface> {

    public static final String KESTRA_LOGS_TYPE_CONFIG = "kestra.logs.type";

    protected final ApplicationContext applicationContext;

    public LogRepositoryInterfaceFactory(final PluginRegistry pluginRegistry,
        final Validator validator,
        final ApplicationContext applicationContext) {
        super(pluginRegistry, validator);
        this.applicationContext = applicationContext;
    }

    /**
     * Constructs and validates a new {@link LogRepositoryInterface} of the given type with the given
     * configuration.
     *
     * @param identifier          the ID of the log store, optionally in the form {@code <id>:<version>}.
     * @param pluginConfiguration the configuration of the log store. May be {@code null}.
     * @return a new, initialized {@link LogRepositoryInterface}.
     */
    public LogRepositoryInterface make(final String identifier,
        final Map<String, Object> pluginConfiguration) {
        LogRepositoryInterface plugin = resolve(identifier, pluginConfiguration);

        if (plugin instanceof ApplicationContextInitializable initializable) {
            initializable.init(applicationContext);
        }

        return plugin;
    }

    @Override
    protected String typeProperty() {
        return KESTRA_LOGS_TYPE_CONFIG;
    }

    @Override
    protected String lookupDisplayName() {
        return "log store";
    }

    @Override
    protected String configDisplayName() {
        return "log store";
    }

    @Override
    protected List<? extends Class<?>> pluginClasses(final RegisteredPlugin plugin) {
        return plugin.getLogStores();
    }
}
