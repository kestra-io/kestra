package io.kestra.core.server;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class ServiceStateTransition {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceStateTransition.class);

    /**
     * Helpers method to get a convenient response from a service state transition.
     *
     * @param initial  the initial or local {@link ServiceInstance}.
     * @param newState The new service state.
     * @param result   The service transition result. An {@link Optional} of {@link ImmutablePair} holding the old (left),
     *                 and new {@link ServiceInstance} or {@code null} if transition failed (right).
     *                 Otherwise, an {@link Optional#empty()} if the no service can be found.
     * @return an optional {@link Response}.
     */
    public static Response logTransitionAndGetResponse(@NotNull final ServiceInstance initial,
                                                       @NotNull final Service.ServiceState newState,
                                                       @Nullable final ImmutablePair<ServiceInstance, ServiceInstance> result) {
        if (result == null) {
            LOG.debug("Failed to transition service [id={}, type={}, hostname={}] to {}. Cause: {}",
                initial.id(),
                initial.type(),
                initial.server().hostname(),
                newState,
                "Invalid service."
            );
            return new Response(Result.ABORTED);
        }

        final ServiceInstance oldInstance = result.getLeft();
        final ServiceInstance newInstance = result.getRight();

        if (newInstance == null) {
            LOG.warn("Failed to transition service [id={}, type={}, hostname={}] from {} to {}. Cause: {}.",
                initial.id(),
                initial.type(),
                initial.server().hostname(),
                oldInstance.state(),
                newState,
                "Invalid transition"
            );
            return new Response(Result.FAILED, oldInstance);
        }

        // Logs if the state was changed, otherwise this method called for heartbeat purpose.
        if (!oldInstance.state().equals(newInstance.state())) {
            LOG.info("Service [id={}, type={}, hostname={}] transition from {} to {}.",
                initial.id(),
                initial.type(),
                initial.server().hostname(),
                oldInstance.state(),
                newInstance.state()
            );
        }
        return new Response(Result.SUCCEED, newInstance);
    }

    /**
     * Wraps a service instance and a transition result.
     *
     * @param instance The service.
     * @param result   The transition result.
     */
    public record Response(Result result, @Nullable ServiceInstance instance) {

        public Response(Result result) {
            this(result, null);
        }

        public boolean is(final Result result) {
            return this.result.equals(result);
        }
    }

    /**
     * Represents the result of a service state transition.
     */
    public enum Result {
        /**
         * State transition succeed.
         */
        SUCCEED,
        /**
         * State transition failed due to invalid state transition.
         */
        FAILED,
        /**
         * State transition cannot be executed; service does not exist.
         */
        ABORTED,
    }
}
