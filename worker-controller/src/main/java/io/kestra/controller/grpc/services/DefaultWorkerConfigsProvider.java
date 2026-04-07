package io.kestra.controller.grpc.services;

import java.util.Map;

import io.kestra.core.reporter.UsageReportConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default {@link WorkerConfigsProvider} that propagates the telemetry configuration.
 */
@Singleton
public class DefaultWorkerConfigsProvider implements WorkerConfigsProvider {

    protected final UsageReportConfig usageReportConfig;

    @Inject
    public DefaultWorkerConfigsProvider(UsageReportConfig usageReportConfig) {
        this.usageReportConfig = usageReportConfig;
    }

    @Override
    public Map<String, Object> get() {
        return Map.of(
            UsageReportConfig.ANONYMOUS_USAGE_REPORT, usageReportConfig
        );
    }
}
