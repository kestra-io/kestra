package io.kestra.core.worker;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * {@code expireAfterAccess} is a safety bound only; entries are normally evicted by push
 * invalidation events sent by the controller, not by TTL.
 */
@ConfigurationProperties("kestra.worker.metastore-cache")
public record MetaStoreCacheConfig(
    @Bindable(defaultValue = "10000") Long maximumSize,
    @Bindable(defaultValue = "PT1H")  Duration expireAfterAccess
) {}
