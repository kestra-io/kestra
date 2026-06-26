package io.kestra.webserver.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.kestra.core.metrics.MetricRegistry;
import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/**
 * Instruments Server-Sent Events (SSE) follow streams with connection metrics, applied at the
 * controller {@link Flux} pipeline — the closest point to the actual SSE connection lifecycle.
 * <p>
 * For each {@code sse_type} it exposes:
 * <ul>
 *   <li>gauge {@code sse.connections.active} — currently-open follow connections, incremented on
 *       subscribe and decremented when the stream terminates (complete / error / cancel / timeout);</li>
 *   <li>counter {@code sse.connections.opened.total} — cumulative opens, whose {@code rate()} exposes
 *       reconnect churn.</li>
 * </ul>
 */
@Singleton
public class SseConnectionMetrics {
    private final MetricRegistry metricRegistry;
    private final Map<String, AtomicInteger> activeByType = new ConcurrentHashMap<>();

    @Inject
    public SseConnectionMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    /**
     * Wrap an SSE follow {@link Flux} so its open/close lifecycle updates the connection metrics for
     * the given {@code type} (e.g. {@code logs}, {@code execution}, {@code dependencies}).
     * <p>
     * The caller's stream cleanup is passed as {@code onClose} and folded into the single
     * {@link Flux#doFinally} this method installs, so the pipeline keeps exactly one {@code doFinally}
     * and the active-connection decrement runs deterministically <em>last</em> — after {@code onClose}
     * (i.e. after the subscriber is unregistered) — on every terminal signal (complete / error / cancel
     * / timeout). Reactor offers no {@code doLast}, and {@code doAfterTerminate} skips {@code cancel}
     * (the usual client-disconnect path), so a single ordered {@code doFinally} is the only way to
     * guarantee this ordering.
     */
    public <T> Flux<Event<T>> track(Flux<Event<T>> flux, String type, Runnable onClose) {
        AtomicInteger active = active(type);
        return flux
            .doOnSubscribe(subscription -> {
                active.incrementAndGet();
                metricRegistry.counter(
                    MetricRegistry.METRIC_SSE_CONNECTIONS_OPENED_TOTAL,
                    MetricRegistry.METRIC_SSE_CONNECTIONS_OPENED_TOTAL_DESCRIPTION,
                    MetricRegistry.TAG_SSE_TYPE, type
                ).increment();
            })
            .doFinally(signalType -> {
                try {
                    onClose.run();
                } finally {
                    active.decrementAndGet();
                }
            });
    }

    /**
     * Lazily create the per-type {@link AtomicInteger} backing the active-connections gauge, registering
     * the gauge on first use. Micronaut reads the backing number on each scrape, so the gauge stays live.
     */
    private AtomicInteger active(String type) {
        return activeByType.computeIfAbsent(type, t -> {
            AtomicInteger counter = new AtomicInteger(0);
            metricRegistry.gauge(
                MetricRegistry.METRIC_SSE_CONNECTIONS_ACTIVE,
                MetricRegistry.METRIC_SSE_CONNECTIONS_ACTIVE_DESCRIPTION,
                counter,
                MetricRegistry.TAG_SSE_TYPE, t
            );
            return counter;
        });
    }
}
