package io.kestra.core.queues.factory;

import java.util.List;
import java.util.Map;

import io.kestra.core.plugins.AbstractPluginInterfaceFactory;
import io.kestra.core.plugins.ApplicationContextInitializable;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.LogDataStoreInterface;

import io.micronaut.context.ApplicationContext;
import jakarta.validation.Validator;

/**
 * Factory for constructing {@link QueueFactoryInterface} objects from configuration.
 * <p>
 * The backend is selected by {@code kestra.queue.type} and discovered as a plugin via the
 * {@link PluginRegistry}; the shared mechanism lives in {@link AbstractPluginInterfaceFactory}.
 */
public class QueuePluginInterfaceFactory extends AbstractPluginInterfaceFactory<QueueFactoryInterface> {

    public static final String KESTRA_QUEUE_TYPE_CONFIG = "kestra.queue.type";

    protected final ApplicationContext applicationContext;

    public QueuePluginInterfaceFactory(final PluginRegistry pluginRegistry,
        final Validator validator,
        final ApplicationContext applicationContext) {
        super(pluginRegistry, validator);
        this.applicationContext = applicationContext;
    }

    /**
     * Constructs and validates a new {@link LogDataStoreInterface} of the given type with the given
     * configuration.
     *
     * @param identifier the ID of the log store, optionally in the form {@code <id>:<version>}.
     * @param pluginConfiguration the configuration of the log store. May be {@code null}.
     * @return a new, initialized {@link LogDataStoreInterface}.
     */
    public QueueFactoryInterface make(final String identifier, final Map<String, Object> pluginConfiguration, QueueBackendDependencies backendDependencies) {
        QueueFactoryInterface plugin = resolve(identifier, pluginConfiguration);
        plugin.init(backendDependencies, pluginConfiguration);

        if (plugin instanceof ApplicationContextInitializable initializable) {
            initializable.init(applicationContext);
        }

        return plugin;
    }

    @Override
    protected String typeProperty() {
        return KESTRA_QUEUE_TYPE_CONFIG;
    }

    @Override
    protected String lookupDisplayName() {
        return "queue factory";
    }

    @Override
    protected String configDisplayName() {
        return "queue factory";
    }

    @Override
    protected List<? extends Class<?>> pluginClasses(final RegisteredPlugin plugin) {
        return plugin.getQueueFactories();
    }
}
