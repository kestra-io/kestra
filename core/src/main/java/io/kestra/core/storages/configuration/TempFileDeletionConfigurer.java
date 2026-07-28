package io.kestra.core.storages.configuration;

import io.kestra.core.utils.FileUtils;
import io.micronaut.context.annotation.Context;

/**
 * Applies {@link TempFileDeletionConfiguration} to the static retry settings of {@link FileUtils} at
 * startup.
 * <p>
 * {@code FileUtils} is a static utility used from many non-managed call sites, so rather than thread
 * the configuration through every caller we push the configured values into it once, eagerly
 * ({@link Context}), before any flow is executed.
 */
@Context
public class TempFileDeletionConfigurer {

    public TempFileDeletionConfigurer(TempFileDeletionConfiguration configuration) {
        FileUtils.configureDeletionRetry(configuration.maxAttempts(), configuration.retryDelay());
    }
}
