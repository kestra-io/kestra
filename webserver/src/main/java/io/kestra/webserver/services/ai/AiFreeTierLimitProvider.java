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
 * The hosted free tier's budget, as Kestra's relay reports it.
 *
 * <p>Fetched rather than configured locally because the ceiling and the rate card are ours, not the operator's:
 * an instance has no way to know the figures, and a local copy is stale the moment we re-price or re-size. The
 * relay is what refuses a turn, so the figure shown has to be the relay's own.
 *
 * <p>Refreshed on a schedule and read from memory, never fetched on the path of a turn. The limit is consulted
 * before every model call, so a synchronous fetch would put an outbound request — and api.kestra.io's
 * availability — between a user and every single answer.
 *
 * <p>Every failure keeps the last known value, and an instance that has never reached the relay reports no limit
 * at all. Failing open is deliberate: the relay enforces its own budget and answers 429 when it is spent, so the
 * worst case of not knowing the limit is a user who is surprised by a refusal, where the worst case of inventing
 * one is a Copilot that refuses turns the relay would have served.
 */
@Singleton
@Requires(property = "kestra.ai.free-tier.enabled", value = "true", defaultValue = "true")
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
     * <p>A limit the operator configured themselves wins over the relay's. They may want to spend less of their
     * allowance than we would let them — the reverse is not theirs to grant, and the relay enforces that anyway.
     */
    public Optional<AiUsageLimitConfiguration> limit() {
        return configuration.usageLimit().or(() -> Optional.ofNullable(fetched.get()));
    }

    /**
     * Picks up a re-price or a re-size without a restart.
     *
     * <p>The initial delay is short but not zero: the limit is only needed once someone takes a turn, and a fetch
     * during startup competes with everything else a booting webserver is doing.
     */
    @Scheduled(fixedDelay = "1h", initialDelay = "30s")
    public void refresh() {
        if (!configuration.isEnabled()) {
            return;
        }

        try {
            // Bound straight into the type that gets used, rather than through a local mirror of the relay's
            // response: the relay serves the same field names, so a mirror would add a class whose only job is to
            // be copied into this one — and the copy is where a renamed field goes unnoticed.
            AiUsageLimitConfiguration limits = client.toBlocking().retrieve(
                HttpRequest.GET(configuration.getBaseUrl() + "/limits"), AiUsageLimitConfiguration.class
            );
            fetched.set(limits);
            log.debug("Fetched the hosted AI free tier's budget from {}: {}", configuration.getBaseUrl(), limits);
        } catch (Exception e) {
            // Debug, not warn: an instance with no outbound access to api.kestra.io still has a working Copilot
            // through the relay, and a recurring warning about a figure it only displays would be noise.
            log.debug(
                "Could not fetch the hosted AI free tier's budget from {}; keeping the last known value. {}",
                configuration.getBaseUrl(), e.getMessage()
            );
        }
    }
}
