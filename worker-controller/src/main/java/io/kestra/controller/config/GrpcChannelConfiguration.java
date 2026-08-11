package io.kestra.controller.config;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for gRPC channel settings.
 * <p>
 * This configuration defines parameters for managing connections
 * to gRPC endpoints, including retry mechanisms and connection keep-alive behavior.
 *
 * @param keepAliveTime Defines the duration for gRPC connection keep-alive.
 * @param shutdownTimeout Defines the maximum time to wait for graceful channel shutdown.
 * @param retry Retry settings, including the switch that turns retry off entirely.
 */
@ConfigurationProperties("kestra.grpc.channel")
public record GrpcChannelConfiguration(
    @Bindable(defaultValue = "1h") Duration keepAliveTime,
    @Bindable(defaultValue = "30s") Duration shutdownTimeout,
    Retry retry) {

    /**
     * Retry configuration.
     * <p>
     * Only the RPCs that are safe to replay are retried, and only on {@code UNAVAILABLE} — see
     * {@code GrpcChannelManager#RETRYABLE_METHODS} for the allow-list and the reasoning behind the
     * exclusions.
     *
     * @param enabled Whether a retry policy is installed at all. Disabling it restores the pre-1.x behavior
     *        where a transient controller loss failed the call outright.
     * @param maxAttempts Maximum number of attempts for a retryable call, including the initial one. Feeds
     *        both the retry policy declared in the channel's service config and the channel-level ceiling, so
     *        the two can never disagree. The gRPC service-config spec requires at least two attempts, so any
     *        lower value leaves retries off entirely.
     * @param initialBackoff Delay before the first retry.
     * @param maxBackoff Ceiling for the exponentially growing delay.
     * @param backoffMultiplier Factor applied to the delay after each failed attempt.
     */
    @ConfigurationProperties("retry")
    public record Retry(
        @Bindable(defaultValue = "true") boolean enabled,
        @Positive @Bindable(defaultValue = "4") int maxAttempts,
        @Bindable(defaultValue = "PT0.5S") Duration initialBackoff,
        @Bindable(defaultValue = "PT5S") Duration maxBackoff,
        @Positive @Bindable(defaultValue = "2.0") double backoffMultiplier) {

        /** Duration bounds cannot be expressed with a jakarta constraint, so they are checked here. */
        public Retry {
            requirePositive(initialBackoff, "initial-backoff");
            requirePositive(maxBackoff, "max-backoff");
            if (maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException(
                    "Property kestra.grpc.channel.retry.max-backoff must be greater than or equal to initial-backoff, but was %s for an initial backoff of %s."
                        .formatted(maxBackoff, initialBackoff)
                );
            }
        }

        private static void requirePositive(final Duration duration, final String property) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(
                    "Property kestra.grpc.channel.retry.%s must be a positive duration, but was %s.".formatted(property, duration)
                );
            }
        }
    }
}
