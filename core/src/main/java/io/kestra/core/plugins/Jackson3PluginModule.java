package io.kestra.core.plugins;

import java.io.Serial;
import java.lang.reflect.Modifier;

import io.kestra.core.app.AppPluginInterface;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetExporter;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.logs.LogExporter;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.plugins.serdes.Jackson3AssetDeserializer;
import io.kestra.core.plugins.serdes.Jackson3PluginDeserializer;
import io.kestra.core.secret.SecretPluginInterface;
import io.kestra.core.storages.StorageInterface;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 counterpart of {@link PluginModule}, registering {@link Jackson3PluginDeserializer} for every
 * supported plugin type on the Micronaut-managed mapper that binds HTTP bodies, plus the plugin-declared
 * extension points that cannot be enumerated (see {@link AdditionalPluginDeserializers}). It also carries the
 * {@link TriggerId} abstract-type mapping, which is not a plugin concern but needs the same mapper (see below).
 *
 * @see io.kestra.core.serializers.ObjectMapperFactory
 */
@SuppressWarnings("this-escape")
public class Jackson3PluginModule extends SimpleModule {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String NAME = "kestra-plugin-jackson3";

    /**
     * Creates a new {@link Jackson3PluginModule} instance.
     */
    public Jackson3PluginModule() {
        super(NAME);
        addDeserializer(ExecutableTask.class, new Jackson3PluginDeserializer<>());
        addDeserializer(Task.class, new Jackson3PluginDeserializer<>());
        addDeserializer(Chart.class, new Jackson3PluginDeserializer<>());
        addDeserializer(DataFilter.class, new Jackson3PluginDeserializer<>());
        addDeserializer(DataFilterKPI.class, new Jackson3PluginDeserializer<>());
        addDeserializer(AbstractTrigger.class, new Jackson3PluginDeserializer<>());
        addDeserializer(TaskRunner.class, new Jackson3PluginDeserializer<>());
        addDeserializer(StorageInterface.class, new Jackson3PluginDeserializer<>());
        addDeserializer(SecretPluginInterface.class, new Jackson3PluginDeserializer<>());
        addDeserializer(AppPluginInterface.class, new Jackson3PluginDeserializer<>());
        addDeserializer(LogExporter.class, new Jackson3PluginDeserializer<>());
        addDeserializer(Asset.class, new Jackson3AssetDeserializer());
        addDeserializer(AssetExporter.class, new Jackson3PluginDeserializer<>());

        // Jackson 3 does not inherit a getter's @JsonDeserialize(as=...) from an implemented interface onto a
        // record's canonical-constructor parameter of the same name, so records with a bare TriggerId field
        // would fail with "no Creators exist" for the interface. Map the abstract type explicitly instead.
        addAbstractTypeMapping(TriggerId.class, TriggerId.Default.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupModule(SetupContext context) {
        // Registered first on purpose: Jackson prepends each provider, so whatever is added last is consulted
        // first, and the exact-type registrations must keep priority over this catch-all.
        context.addDeserializers(new AdditionalPluginDeserializers());
        super.setupModule(context);
    }

    /**
     * Resolves every abstract {@link AdditionalPlugin} subtype through {@link Jackson3PluginDeserializer}.
     * <p>
     * A plugin's extension point is a plugin-defined subtype of {@link AdditionalPlugin}, so the constructor's
     * exact-type registrations cannot enumerate them; plugins declare it with
     * {@code @JsonDeserialize(using = PluginDeserializer.class)}, which Micronaut's compatibility layer ignores
     * because it never overrides {@code findDeserializer}. Matching by assignability keeps that contract working
     * here without every plugin having to ship a Jackson 3 deserializer.
     */
    private static class AdditionalPluginDeserializers implements Deserializers {
        /**
         * {@inheritDoc}
         */
        @Override
        public ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription.Supplier beanDescRef) {
            return hasDeserializerFor(config, type.getRawClass()) ? new Jackson3PluginDeserializer<>() : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            // Abstract types only: Jackson3PluginDeserializer resolves the concrete type and reads it back
            // through the context, so claiming concrete types too would make it re-invoke itself forever.
            return AdditionalPlugin.class.isAssignableFrom(valueType) && Modifier.isAbstract(valueType.getModifiers());
        }
    }
}
