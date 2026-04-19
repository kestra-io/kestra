package io.kestra.core.server;

import java.time.Instant;

/**
 * Hook invoked after a successful service liveness state update, on both the
 * scheduled heartbeat tick and event-driven state transitions.
 * <p>
 * Implementations are registered as Micronaut beans and discovered automatically
 * by {@link ServiceLivenessManager}. They must be fast and non-throwing:
 * exceptions are caught and logged by the manager so that a broken listener
 * does not break the liveness pipeline.
 * <p>
 * Invocation order across registered listeners is not guaranteed.
 */
public interface ServiceLivenessListener {

    /**
     * Called with the latest remote view of a service instance once its liveness
     * state update has been confirmed (either {@code SUCCEEDED} or recovered via
     * {@code ABORTED}). Not invoked on {@code FAILED} transitions.
     *
     * @param now      the instant the update happened.
     * @param instance the service instance after the successful update.
     * @param newState the state the instance is in after the update.
     */
    void onLivenessUpdate(Instant now, ServiceInstance instance, Service.ServiceState newState);
}
