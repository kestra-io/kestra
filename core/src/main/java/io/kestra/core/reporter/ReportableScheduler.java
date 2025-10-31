package io.kestra.core.reporter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;

@Singleton
@Requires(property = "kestra.anonymous-usage-report.enabled", value = "true")
@Requires(property = "kestra.server-type")
@Slf4j
public class ReportableScheduler {

    private final ReportableRegistry registry;
    private final ServerEventSender sender;
    private final Clock clock;

    @Inject
    public ReportableScheduler(ReportableRegistry registry, ServerEventSender sender) {
        this.registry = registry;
        this.sender = sender;
        this.clock = Clock.systemDefaultZone();
    }

    @Scheduled(fixedDelay = "5m", initialDelay = "${kestra.anonymous-usage-report.initial-delay:5m}")
    public void tick() {
        Instant now = clock.instant();
        for (Reportable<?> r : registry.getAll()) {
            if (r.isEnabled() && r.schedule().shouldRun(now)) {
                try {
                    Object value = r.report(now);
                    if (value != null) sender.send(now, r.type(), value);
                } catch (Exception e) {
                    log.debug("Failed to send report for event-type '{}'", r.type(), e);
                }
            }
        }
    }
}
