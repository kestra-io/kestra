package io.kestra.webserver.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.ReadableBytes;

/**
 * Configuration of the bounded in-memory cache used to serve UI and plugin-UI static resources.
 *
 * @param maxSize the maximum total heap size of all cached UI resources, raw and precompressed variants included.
 */
@ConfigurationProperties("kestra.webserver.ui-resource-cache")
public record UiResourceCacheConfiguration(
    @Bindable(defaultValue = UiResourceCacheConfiguration.DEFAULT_MAX_SIZE) @ReadableBytes Long maxSize
) {
    public static final String DEFAULT_MAX_SIZE = "64mb";
}
