package io.kestra.core.storages.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration for the deletion of the transient files created while uploading to the internal storage.
 * <p>
 * On Windows a file that has just been closed can stay briefly locked (asynchronous handle release,
 * antivirus or indexing), making the deletion fail. Retrying after a short delay clears this in
 * virtually all cases. These properties tune that retry behaviour and are bound from
 * {@code kestra.storage.temp-file-deletion.*}.
 *
 * @param maxAttempts the number of deletion attempts before giving up and logging a warning.
 * @param retryDelay  the delay between two deletion attempts.
 */
@ConfigurationProperties("kestra.storage.temp-file-deletion")
public record TempFileDeletionConfiguration(
    @Bindable(defaultValue = "5") Integer maxAttempts,
    @Bindable(defaultValue = "50ms") Duration retryDelay
) {
}
