package io.kestra.webserver.services.ai;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The hosted free tier's budget, as Kestra's relay reports it. The ceiling and the rate card are the relay's to
 * set, so an instance cannot know them and a local copy would go stale on every re-price.
 *
 * <p>Refreshed on a schedule and read from memory, never fetched on the path of a turn: the limit is consulted
 * before every model call, so a synchronous fetch would put api.kestra.io's availability between a user and
 * every answer.
 *
 * <p>Failures keep the last known value, and an instance that has never reached the relay reports no limit.
 * Failing open is deliberate — the relay enforces its own budget and answers 429, so not knowing the ceiling
 * costs a surprising refusal, where inventing one would refuse turns the relay would have served.
 */
@Singleton
@Requires(condition = AiFreeTierEnabledCondition.class)
@Slf4j
public class AiFreeTierLimitProvider {
    private final AiFreeTierConfiguration configuration;
    private final ReactorHttpClient client;
    private final AtomicReference<AiUsageLimitConfiguration> fetched = new AtomicReference<>();

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
        return Optional.ofNullable(fetched.get()).filter(AiUsageLimitConfiguration::enabled);
    }

    /** Picks up a re-price or a re-size without a restart. Delayed past startup, since no turn can have run yet. */
    @Scheduled(fixedDelay = "1h", initialDelay = "30s")
    public void refresh() {
        // Redundant with AiFreeTierEnabledCondition, which should have kept this bean from existing — but what
        // it guards is an outbound call from a deployment that opted out.
        if (!configuration.isEnabled()) {
            return;
        }

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
