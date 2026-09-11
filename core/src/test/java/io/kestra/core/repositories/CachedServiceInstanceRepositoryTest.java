package io.kestra.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CachedServiceInstanceRepositoryTest {

    private ServiceInstanceRepositoryInterface delegateRepository;
    private ServiceLivenessStore delegateLivenessStore;
    private CachedServiceInstanceRepository cachedRepository;

    @BeforeEach
    void setUp() {
        delegateRepository = mock(ServiceInstanceRepositoryInterface.class, withSettings().extraInterfaces(ServiceLivenessStore.class));
        delegateLivenessStore = (ServiceLivenessStore) delegateRepository;
        cachedRepository = new CachedServiceInstanceRepository(delegateRepository, delegateLivenessStore);
    }

    private ServiceInstance createTestInstance(String id, Service.ServiceState state) {
        return ServiceInstance.builder()
            .id(id)
            .type(ServiceType.WORKER)
            .state(state)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void shouldReturnCachedValueOnFindByIdCacheHit() {
        ServiceInstance instance = createTestInstance("service-1", Service.ServiceState.RUNNING);
        when(delegateRepository.findById("service-1")).thenReturn(Optional.of(instance));

        Optional<ServiceInstance> firstCall = cachedRepository.findById("service-1");
        Optional<ServiceInstance> secondCall = cachedRepository.findById("service-1");

        assertThat(firstCall).isPresent().contains(instance);
        assertThat(secondCall).isPresent().contains(instance);
        verify(delegateRepository, times(1)).findById("service-1");
    }

    @Test
    void shouldReturnCachedInstancesInStatesOnCacheHit() {
        ServiceInstance instance1 = createTestInstance("worker-1", Service.ServiceState.RUNNING);
        Set<Service.ServiceState> states = Set.of(Service.ServiceState.RUNNING);
        when(delegateLivenessStore.findAllInstancesInStates(states)).thenReturn(List.of(instance1));

        List<ServiceInstance> firstCall = cachedRepository.findAllInstancesInStates(states);
        List<ServiceInstance> secondCall = cachedRepository.findAllInstancesInStates(states);

        assertThat(firstCall).hasSize(1).contains(instance1);
        assertThat(secondCall).hasSize(1).contains(instance1);
        verify(delegateLivenessStore, times(1)).findAllInstancesInStates(states);
    }

    @Test
    void shouldInvalidateCacheOnSave() {
        ServiceInstance instance = createTestInstance("service-1", Service.ServiceState.RUNNING);
        when(delegateRepository.findById("service-1")).thenReturn(Optional.of(instance));
        when(delegateRepository.save(any())).thenReturn(instance);

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(1)).findById("service-1");

        cachedRepository.save(instance);

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(2)).findById("service-1");
    }

    @Test
    void shouldInvalidateCacheOnDelete() {
        ServiceInstance instance = createTestInstance("service-1", Service.ServiceState.RUNNING);
        when(delegateRepository.findById("service-1")).thenReturn(Optional.of(instance));

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(1)).findById("service-1");

        cachedRepository.delete(instance);

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(2)).findById("service-1");
    }

    @Test
    void shouldInvalidateCacheOnStateTransition() {
        ServiceInstance instance = createTestInstance("service-1", Service.ServiceState.RUNNING);
        when(delegateRepository.findById("service-1")).thenReturn(Optional.of(instance));

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(1)).findById("service-1");

        cachedRepository.mayTransitServiceTo(null, instance, Service.ServiceState.TERMINATED_GRACEFULLY, "test transition");

        cachedRepository.findById("service-1");
        verify(delegateRepository, times(2)).findById("service-1");
    }
}
