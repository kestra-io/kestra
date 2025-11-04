package io.kestra.core.server;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.time.Instant;
import java.util.Optional;

@Singleton
public class DefaultServiceLivenessUpdater implements ServiceLivenessUpdater {

    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    @Inject
    public DefaultServiceLivenessUpdater(ServiceInstanceRepositoryInterface serviceInstanceRepository) {
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    @Override
    public void update(ServiceInstance service) {
        this.serviceInstanceRepository.save(service);
    }

    @Override
    public ServiceStateTransition.Response update(final ServiceInstance instance,
                                                  final Service.ServiceState newState,
                                                  final String reason) {
        // FIXME this is a quick & dirty refacto to make it work cross queue but it needs to be carefully reworked to allow transaction if supported
        return mayTransitServiceTo(instance, newState, reason);
    }

    /**
     * Attempt to transition the state of a given service to given new state.
     * This method may not update the service if the transition is not valid.
     *
     * @param instance the service instance.
     * @param newState the new state of the service.
     * @return an optional of the {@link ServiceInstance} or {@link Optional#empty()} if the service is not running.
     */
    private ServiceStateTransition.Response mayTransitServiceTo(final ServiceInstance instance,
                                                               final Service.ServiceState newState,
                                                               final String reason) {
        ImmutablePair<ServiceInstance, ServiceInstance> result = mayUpdateStatusById(
            instance,
            newState,
            reason
        );
        return ServiceStateTransition.logTransitionAndGetResponse(instance, newState, result);
    }

    /**
     * Attempt to transition the state of a given service to given new state.
     * This method may not update the service if the transition is not valid.
     *
     * @param instance the new service instance.
     * @param newState the new state of the service.
     * @return an {@link Optional} of {@link ImmutablePair} holding the old (left), and new {@link ServiceInstance} or {@code null} if transition failed (right).
     * Otherwise, an {@link Optional#empty()} if the no service can be found.
     */
    private ImmutablePair<ServiceInstance, ServiceInstance> mayUpdateStatusById(final ServiceInstance instance,
                                                                                final Service.ServiceState newState,
                                                                                final String reason) {
        // Find the ServiceInstance to be updated
        Optional<ServiceInstance> optional = serviceInstanceRepository.findById(instance.uid());

        // Check whether service was found.
        if (optional.isEmpty()) {
            return null;
        }

        // Check whether the status transition is valid before saving.
        final ServiceInstance before = optional.get();
        if (before.state().isValidTransition(newState)) {
            ServiceInstance updated = before
                .state(newState, Instant.now(), reason)
                .server(instance.server())
                .metrics(instance.metrics());
            // Synchronize
            update(updated);
            return new ImmutablePair<>(before, updated);
        }
        return new ImmutablePair<>(before, null);
    }
}
