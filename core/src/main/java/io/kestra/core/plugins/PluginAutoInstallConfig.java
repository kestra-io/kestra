package io.kestra.core.plugins;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration of the plugin auto-install feature ({@code kestra.plugins.auto-install}).
 *
 * @param enabled whether missing plugins are auto-installed on flow save. Unset means the
 *        computed default: on for OSS with local-filesystem storage, off everywhere else.
 * @param installTimeout bounded wait for the boot-time and first-sync-migration installs.
 * @param saveTimeout bounded wait for the synchronous save-path install hook, shorter so a
 *        bulk import never serializes minutes per flow behind the install pool.
 * @param concurrency size of the install thread pool; {@code 0} (the default) computes it
 *        from the allocated CPU cores.
 */
@ConfigurationProperties(PluginAutoInstallConfig.PREFIX)
public record PluginAutoInstallConfig(
    Optional<Boolean> enabled,
    @Bindable(defaultValue = "PT2M") Duration installTimeout,
    @Bindable(defaultValue = "PT30S") Duration saveTimeout,
    @Bindable(defaultValue = "0") int concurrency) {

    public static final String PREFIX = "kestra.plugins.auto-install";
}
