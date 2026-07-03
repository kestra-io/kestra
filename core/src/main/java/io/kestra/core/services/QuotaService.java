package io.kestra.core.services;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.quota.Quota;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Service to manage quotas.
 * As Quota is an EE feature, this service throws unconditionally.
 */
@Singleton
public class QuotaService {
    /**
     * Check and increment all quotas for the given flow.
     * When a quota is exceeded, all other quotas are not incremented and the quota is returned.
     *
     * @return the quota that was exceeded if any
     */
    public Optional<Quota> checkAndIncrement(FlowInterface flow) {
        throw new UnsupportedOperationException("Quotas are an EE feature");
    }
}
