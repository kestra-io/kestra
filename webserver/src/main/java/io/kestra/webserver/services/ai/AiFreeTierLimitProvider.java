package io.kestra.webserver.services.ai;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The hosted free tier's budget, as Kestra's relay reports it. The ceiling and the rate card are the relay's to
 * set, so an instance cannot know them and a local copy would go stale on every re-price.
 *
 * <p>Read from memory and refreshed in the background when the held copy has aged out, never fetched on the
 * path of a turn: the limit is consulted before every model call, so a synchronous fetch would put
 * api.kestra.io's availability between a user and every answer. A read triggers at most one refresh, and the
 * value it returns is whatever is already held — including nothing, on the first read of all.
 *
 * <p>Failures keep the last known value, and an instance that has never reached the relay reports no limit.
 * Failing open is deliberate — the relay enforces its own budget and answers 429, so not knowing the ceiling
 * costs a surprising refusal, where inventing one would refuse turns the relay would have served.
 */
@Singleton
@Requires(condition = AiFreeTierEnabledCondition.class)
@Slf4j
public class AiFreeTierLimitProvider {
    /** Nothing fetched yet. Never stored, so it cannot be mistaken for a reading. */
    private static final long NEVER = Long.MIN_VALUE;

    private final AiFreeTierConfiguration configuration;
    private final ReactorHttpClient client;
    private final AtomicReference<AiUsageLimitConfiguration> fetched = new AtomicReference<>();
    private final AtomicLong lastAttemptedAt = new AtomicLong(NEVER);
    private final AtomicBoolean refreshing = new AtomicBoolean();

    @Inject
    public AiFreeTierLimitProvider(final AiFreeTierConfiguration configuration, final ReactorHttpClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    /**
     * The limit to apply to hosted spend, or empty when there is none to apply yet.
     *
     * <p>Filtered on {@code enabled} as a declared provider's own ceiling is, so a relay reporting a
     * switched-off limit reads as no ceiling rather than an active one with nothing behind it.
     */
    public Optional<AiUsageLimitConfiguration> limit() {
        refreshIfStale();

        return Optional.ofNullable(fetched.get()).filter(AiUsageLimitConfiguration::enabled);
    }

    /**
     * Starts a refresh when the held copy has aged out, and returns without waiting for it. That is what picks
     * up a re-price or a re-size without a restart, and what keeps the first read after startup from paying
     * for it.
     */
    private void refreshIfStale() {
        // Redundant with AiFreeTierEnabledCondition, which should have kept this bean from existing — but what
        // it guards is an outbound call from a deployment that opted out.
        if (!configuration.isEnabled() || !isStale()) {
            return;
        }

        // At most one fetch outstanding. Without this a slow relay would collect one request per model call,
        // which is the rate this is read at.
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }

        Mono.fromRunnable(this::refresh)
            .subscribeOn(Schedulers.boundedElastic())
            .doFinally(_ -> refreshing.set(false))
            .subscribe(
                ignored -> { },
                // refresh() absorbs its own failures; this is only reached by something it cannot catch.
                e -> log.debug("The hosted AI free tier's budget could not be refreshed. {}", e.getMessage())
            );
    }

    /** Elapsed nanoseconds rather than wall clock: {@code nanoTime} is only meaningful as a difference. */
    private boolean isStale() {
        long last = lastAttemptedAt.get();

        return last == NEVER
            || System.nanoTime() - last >= configuration.getLimitRefreshInterval().toNanos();
    }

    /**
     * Fetches the budget, blocking on the relay. Only ever called off the reading thread, by
     * {@link #refreshIfStale()}.
     */
    void refresh() {
        // Stamped before the call rather than after it, so a relay that is timing out is retried on the same
        // cadence as one that answers, instead of on every read.
        lastAttemptedAt.set(System.nanoTime());

        try {
            AiUsageLimitConfiguration limits = client.toBlocking().retrieve(
                HttpRequest.GET(configuration.getBaseUrl() + "/limits"), AiUsageLimitConfiguration.class
            );
            fetched.set(limits);
            log.debug("Fetched the hosted AI free tier's budget from {}: {}", configuration.getBaseUrl(), limits);
        } catch (Exception e) {
            // Debug, not warn: this figure is only displayed, and the relay still enforces its own budget.
            log.debug(
                "Could not fetch the hosted AI free tier's budget from {}; keeping the last known value. {}",
                configuration.getBaseUrl(), e.getMessage()
            );
        }
    }
}
