package io.kestra.jdbc.runner;

import java.util.Map;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

/**
 * Captures the complete {@code kestra.queue.jdbc} configuration, including JDBC pool options.
 */
@ConfigurationProperties("kestra.queue")
public record QueueJdbcConfiguration(
    @Nullable
    @MapFormat(keyFormat = StringConvention.CAMEL_CASE, transformation = MapFormat.MapTransformation.NESTED) Map<String, Object> jdbc) {

    /**
     * @return the configured JDBC queue type, if any.
     */
    public Optional<String> type() {
        return Optional.ofNullable(getJdbcConfig().get("type")).map(Object::toString);
    }

    /**
     * @return the JDBC configuration, or an empty map when none is provided.
     */
    public Map<String, Object> getJdbcConfig() {
        return jdbc == null ? Map.of() : jdbc;
    }
}
