package io.kestra.core.storages.kv.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.kv.purge-expired")
public record KVPurgeConfiguration(
    @Bindable(defaultValue = "1000") Integer batchSize) {
}
