package io.kestra.core.plugins;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.app.AppPluginInterface;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.serdes.PluginDeserializer;
import io.kestra.core.secret.SecretPluginInterface;
import io.kestra.core.storages.StorageInterface;

import java.io.Serial;

/**
 * Jackson module for registering the {@link PluginDeserializer} for
 * all supported plugin type.
 */
@SuppressWarnings("this-escape")
public class PluginModule extends SimpleModule {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String NAME = "kestra-plugin";

    /**
     * Creates a new {@link PluginModule} instance.
     */
    public PluginModule() {
        super(NAME);
        addDeserializer(ExecutableTask.class, new PluginDeserializer<>());
        addDeserializer(Task.class, new PluginDeserializer<>());
        addDeserializer(Chart.class, new PluginDeserializer<>());
        addDeserializer(DataFilter.class, new PluginDeserializer<>());
        addDeserializer(AbstractTrigger.class, new PluginDeserializer<>());
        addDeserializer(Condition.class, new PluginDeserializer<>());
        addDeserializer(TaskRunner.class, new PluginDeserializer<>());
        addDeserializer(StorageInterface.class, new PluginDeserializer<>());
        addDeserializer(SecretPluginInterface.class, new PluginDeserializer<>());
        addDeserializer(AppPluginInterface.class, new PluginDeserializer<>());
    }
}
