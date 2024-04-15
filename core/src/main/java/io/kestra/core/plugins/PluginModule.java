package io.kestra.core.plugins;


import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.listeners.Listener;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.serdes.PluginDeserializer;
import io.kestra.core.secret.SecretPluginInterface;
import io.kestra.core.storages.StorageInterface;

/**
 * Jackson module for registering the {@link PluginDeserializer} for
 * all supported plugin type.
 */
public class PluginModule extends SimpleModule {

    public static final String NAME = "kestra-plugin";

    /**
     * Creates a new {@link PluginModule} instance.
     */
    public PluginModule() {
        super(NAME);
        addDeserializer(Task.class, new PluginDeserializer<>());
        addDeserializer(AbstractTrigger.class, new PluginDeserializer<>());
        addDeserializer(Condition.class, new PluginDeserializer<>());
        addDeserializer(TaskRunner.class, new PluginDeserializer<>());
        addDeserializer(StorageInterface.class, new PluginDeserializer<>());
        addDeserializer(SecretPluginInterface.class, new PluginDeserializer<>());
    }
}
