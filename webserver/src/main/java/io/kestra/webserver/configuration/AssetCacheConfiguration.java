package io.kestra.webserver.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.ReadableBytes;

/**
 * Configuration of the bounded in-memory cache used to serve UI and plugin-UI static assets.
 *
 * @param maxSize the maximum total heap size of all cached assets, raw and precompressed variants included.
 */
@ConfigurationProperties("kestra.webserver.asset-cache")
public record AssetCacheConfiguration(
    @Bindable(defaultValue = AssetCacheConfiguration.DEFAULT_MAX_SIZE) @ReadableBytes Long maxSize
) {
    public static final String DEFAULT_MAX_SIZE = "64mb";
}
