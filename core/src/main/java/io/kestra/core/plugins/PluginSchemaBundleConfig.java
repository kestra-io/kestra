package io.kestra.core.plugins;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration of the pre-baked plugin schema bundle ({@code kestra.plugins.schema-bundle-*}),
 * consumed by {@link PluginSchemaBundleService}.
 *
 * @param schemaBundlePath an explicit local file, highest priority — see
 *        {@link PluginSchemaBundleService#resolveBundleSource}.
 * @param schemaBundleUrlTemplate a remote URL template ({@code {version}} placeholder), lowest
 *        priority fallback when no bundle is embedded in the jar.
 */
@ConfigurationProperties(PluginSchemaBundleConfig.PREFIX)
public record PluginSchemaBundleConfig(
    Optional<String> schemaBundlePath,
    Optional<String> schemaBundleUrlTemplate) {

    public static final String PREFIX = "kestra.plugins";
}
