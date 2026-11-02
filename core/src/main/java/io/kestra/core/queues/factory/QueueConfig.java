package io.kestra.core.queues.factory;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Map;
import java.util.Optional;

/**
 * Configuration for the pluggable queue factory, bound from {@code kestra.queue.*}.
 * <p>
 * Mirrors {@code KestraBeansFactory.StorageConfig}: the {@code queue} map field under the
 * {@code kestra} prefix captures the whole {@code kestra.queue} subtree, so both the selector
 * ({@code kestra.queue.type}) and the per-type sub-configuration ({@code kestra.queue.<type>.*})
 * can be read from a single bean.
 */
@ConfigurationProperties("kestra")
public record QueueConfig(
    @Nullable
    @MapFormat(keyFormat = StringConvention.CAMEL_CASE, transformation = MapFormat.MapTransformation.NESTED) Map<String, Object> queue) {

    /**
     * @return the configured {@code kestra.queue.type}, if any.
     */
    public Optional<String> type() {
        return Optional.ofNullable(queue)
            .map(m -> m.get("type"))
            .map(Object::toString);
    }

    /**
     * Returns the per-type configuration sub-map for the configured queue factory.
     *
     * @param type the resolved queue factory type.
     * @return the configuration, or an empty map when none is provided.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getQueueConfig(String type) {
        if (queue == null) {
            return Map.of();
        }
        Object config = queue.get(StringConvention.CAMEL_CASE.format(type));
        return config == null ? Map.of() : (Map<String, Object>) config;
    }
}
