package io.kestra.core.reporter;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Slf4j
public class ReportableRegistry {
    
    private final Map<Type, Reportable<?>> reportables = new ConcurrentHashMap<>();
    
    /**
     * Creates a new {@link ReportableRegistry} instance.
     *
     * @param reportables The {@link Reportable reportables}
     */
    @Inject
    public ReportableRegistry(final List<Reportable<?>> reportables) {
        reportables.forEach(reportable -> this.reportables.put(reportable.type(), reportable));
    }
    
    public void register(final Reportable<?> reportable) {
        Objects.requireNonNull(reportable, "reportable must not be null");
        if (reportables.containsKey(reportable.type())) {
            log.warn("Event already registered for type '{}'", reportable.type());
        } else {
            reportables.put(reportable.type(), reportable);
        }
    }
    
    public List<Reportable<?>> getAll() {
        return List.copyOf(reportables.values());
    }
}
