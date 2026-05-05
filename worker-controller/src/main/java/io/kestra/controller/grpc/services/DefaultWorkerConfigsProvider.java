package io.kestra.controller.grpc.services;

import java.util.HashMap;
import java.util.Map;

import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.reporter.UsageReportConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default {@link WorkerConfigsProvider} that propagates the telemetry and encryption configuration.
 */
@Singleton
public class DefaultWorkerConfigsProvider implements WorkerConfigsProvider {

    protected final UsageReportConfig usageReportConfig;
    protected final EncryptionConfig encryptionConfig;

    @Inject
    public DefaultWorkerConfigsProvider(UsageReportConfig usageReportConfig, EncryptionConfig encryptionConfig) {
        this.usageReportConfig = usageReportConfig;
        this.encryptionConfig = encryptionConfig;
    }

    @Override
    public Map<String, Object> get() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(UsageReportConfig.ANONYMOUS_USAGE_REPORT, usageReportConfig);
        encryptionConfig.asOptional().ifPresent(key ->
            configs.put(EncryptionConfig.WORKER_CONFIG_KEY, key)
        );
        return Map.copyOf(configs);
    }
}
