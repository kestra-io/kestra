package io.kestra.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.server.ServiceType;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * A cached wrapper around {@link ServiceInstanceRepositoryInterface} and {@link ServiceLivenessStore}.
 * <p>
 * This class caches lookups for active service instances to reduce database query load during high-frequency operations.
 * Caches are automatically invalidated on write/mutation operations (save, delete, state transitions)
 * and have a short Time-To-Live (TTL) for multi-node cluster safety.
 */
public class CachedServiceInstanceRepository implements ServiceInstanceRepositoryInterface, ServiceLivenessStore {

    private final ServiceInstanceRepositoryInterface delegate;
    private final ServiceLivenessStore livenessStoreDelegate;

    private final Cache<String, Optional<ServiceInstance>> findByIdCache;
    private final Cache<String, List<ServiceInstance>> findAllCache;
    private final Cache<Set<Service.ServiceState>, List<ServiceInstance>> instancesInStatesCache;

    public CachedServiceInstanceRepository(ServiceInstanceRepositoryInterface delegate) {
        this(delegate, delegate instanceof ServiceLivenessStore store ? store : null);
    }

    public CachedServiceInstanceRepository(ServiceInstanceRepositoryInterface delegate, ServiceLivenessStore livenessStoreDelegate) {
        this.delegate = delegate;
        this.livenessStoreDelegate = livenessStoreDelegate;

        this.findByIdCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

        this.findAllCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(10)
            .build();

        this.instancesInStatesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();
    }

    /**
     * Explicitly invalidates all internal cache entries.
     */
    public void invalidateAll() {
        findByIdCache.invalidateAll();
        findAllCache.invalidateAll();
        instancesInStatesCache.invalidateAll();
    }

    @Override
    public Optional<ServiceInstance> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return findByIdCache.get(id, delegate::findById);
    }

    @Override
    public List<ServiceInstance> findAll() {
        return findAllCache.get("ALL", k -> delegate.findAll());
    }

    @Override
    public List<ServiceInstance> findAllInstancesInStates(Set<Service.ServiceState> states) {
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        if (livenessStoreDelegate != null) {
            return instancesInStatesCache.get(states, livenessStoreDelegate::findAllInstancesInStates);
        }
        if (delegate instanceof ServiceLivenessStore store) {
            return instancesInStatesCache.get(states, store::findAllInstancesInStates);
        }
        return List.of();
    }

    @Override
    public List<ServiceInstance> findAllInstancesInState(Service.ServiceState state) {
        if (state == null) {
            return List.of();
        }
        return findAllInstancesInStates(Set.of(state));
    }

    @Override
    public ArrayListTotal<ServiceInstance> find(Pageable pageable, @Nullable List<QueryFilter> filters) {
        return delegate.find(pageable, filters);
    }

    @Override
    public void delete(ServiceInstance service) {
        delegate.delete(service);
        invalidateAll();
    }

    @Override
    public ServiceInstance save(ServiceInstance service) {
        ServiceInstance saved = delegate.save(service);
        invalidateAll();
        return saved;
    }

    @Override
    public List<ServiceInstance> findAllInstancesBetween(ServiceType type, Instant from, Instant to) {
        return delegate.findAllInstancesBetween(type, from, to);
    }

    @Override
    public void processAllNonRunningInstances(BiConsumer<TransactionContext, ServiceInstance> consumer) {
        delegate.processAllNonRunningInstances(consumer);
        invalidateAll();
    }

    @Override
    public ServiceStateTransition.Response mayTransitServiceTo(TransactionContext txContext, ServiceInstance instance, Service.ServiceState newState, String reason) {
        ServiceStateTransition.Response response = delegate.mayTransitServiceTo(txContext, instance, newState, reason);
        invalidateAll();
        return response;
    }

    @Override
    public void processInstanceInStates(Set<Service.ServiceState> states, BiConsumer<TransactionContext, ServiceInstance> consumer) {
        delegate.processInstanceInStates(states, consumer);
        invalidateAll();
    }

    @Override
    public int purgeEmptyInstances(Instant until) {
        int count = delegate.purgeEmptyInstances(until);
        if (count > 0) {
            invalidateAll();
        }
        return count;
    }

    @Override
    public Function<String, String> sortMapping() {
        return delegate.sortMapping();
    }
}
