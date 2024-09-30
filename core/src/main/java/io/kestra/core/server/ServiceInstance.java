package io.kestra.core.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.server.Service.ServiceState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runtime information about a Kestra's service (e.g., WORKER, EXECUTOR, etc.).
 *
 * @param id        The service unique identifier.
 * @param type      The service type.
 * @param state     The state of the service.
 * @param server    The server running this service.
 * @param createdAt Instant when this service was created.
 * @param updatedAt Instant when this service was updated.
 * @param events    The last of events attached to this service - used to provide some contextual information about a state changed.
 * @param config    The server configuration and liveness.
 * @param props     The server additional properties - an opaque map of key/value pairs.
 * @param seqId     A monolithic sequence id which is incremented each time the service instance is updated.
 *                  Used to detect non-transactional update of the instance.
 */
@JsonInclude
public record ServiceInstance(
    String id,
    Service.ServiceType type,
    ServiceState state,
    ServerInstance server,
    Instant createdAt,
    Instant updatedAt,
    List<TimestampedEvent> events,
    ServerConfig config,
    Map<String, Object> props,
    Set<Metric> metrics,
    long seqId
) {

    // TimestampedEvent type for state updated.
    private static final String SERVICE_STATE_UPDATED_EVENT_TYPE = "service.state.updated";

    /**
     * Factory method for constructing an initial {@link ServiceInstance} with {@link ServiceState#CREATED} state.
     *
     * @return a new {@link ServiceInstance}.
     */
    public static ServiceInstance create(final String id,
                                         final Service.ServiceType type,
                                         final ServerInstance server,
                                         final Instant createdAt,
                                         final Instant updatedAt,
                                         final ServerConfig config,
                                         final Map<String, Object> props,
                                         final Set<Metric> metrics) {
        return new ServiceInstance(
            id,
            type,
            ServiceState.CREATED,
            server,
            createdAt,
            updatedAt,
            List.of(new TimestampedEvent(updatedAt, "Service connected.", SERVICE_STATE_UPDATED_EVENT_TYPE, ServiceState.CREATED)),
            config,
            props,
            metrics
        );
    }

    public ServiceInstance(
        String id,
        Service.ServiceType type,
        ServiceState state,
        ServerInstance server,
        Instant createdAt,
        Instant updatedAt,
        List<TimestampedEvent> events,
        ServerConfig config,
        Map<String, Object> props,
        Set<Metric> metrics
    ) {
        this(id, type, state, server, createdAt, updatedAt, events, config, props, metrics, 0L);
    }

    /**
     * Checks service type.
     *
     * @param type the type to check.
     * @return {@code true} if this instance is of the given type.
     */
    public boolean is(final Service.ServiceType type) {
        return this.type.equals(type);
    }

    /**
     * Check service state.
     *
     * @param state the state to check.
     * @return {@code true} if this instance is in the given state.
     */
    public boolean is(final ServiceState state) {
        return this.state.equals(state);
    }

    /**
     * Updates the server for this instance.
     *
     * @param server The new server.
     * @return a new {@link ServiceInstance}.
     */
    public ServiceInstance server(final ServerInstance server) {
        return new ServiceInstance(
            id,
            type,
            state,
            server,
            createdAt,
            updatedAt,
            events,
            config,
            props,
            metrics,
            seqId
        );
    }

    /**
     * Updates the metrics for this instance.
     *
     * @param metrics The new metrics.
     * @return a new {@link ServiceInstance}.
     */
    public ServiceInstance metrics(final Set<Metric> metrics) {
        return new ServiceInstance(
            id,
            type,
            state,
            server,
            createdAt,
            updatedAt,
            events,
            config,
            props,
            metrics,
            seqId
        );
    }

    /**
     * Updates this service instance with the given state and instant.
     *
     * @param newState  The new state.
     * @param updatedAt The update instant
     * @return a new {@link ServiceInstance}.
     */
    public ServiceInstance state(final ServiceState newState,
                                 final Instant updatedAt) {
        return state(newState, updatedAt, null);
    }

    /**
     * Updates this service instance with the given state and instant.
     *
     * @param newState  The new state.
     * @param updatedAt The update instant
     * @param reason    The human-readable reason of the update.
     * @return a new {@link ServiceInstance}.
     */
    public ServiceInstance state(final ServiceState newState,
                                 final Instant updatedAt,
                                 String reason) {

        // add a default reason if a state changed is detected.
        if (reason == null && !state.equals(newState)) {
            reason = String.format("Service transitioned to the '%s' state.", newState);
        }

        List<TimestampedEvent> events = this.events;
        if (reason != null) {
            events = new ArrayList<>(events);
            events.add(new TimestampedEvent(updatedAt, reason, SERVICE_STATE_UPDATED_EVENT_TYPE, newState));
        }
        long nextSeqId = seqId + 1;
        return new ServiceInstance(
            id,
            type,
            newState,
            server,
            createdAt,
            updatedAt,
            events,
            config,
            props,
            metrics,
            nextSeqId
        );
    }

    /**
     * Checks whether the session timeout elapsed for this service.
     *
     * @param now The instant.
     * @return {@code true} if the session for this service has timeout, otherwise {@code false}.
     */
    public boolean isSessionTimeoutElapsed(final Instant now) {
        Duration timeout = this.config.liveness().timeout();
        return this.state.isRunning() && updatedAt().plus(timeout).isBefore(now);
    }

    /**
     * Checks whether the termination grace period elapsed for this service.
     *
     * @param now The instant.
     * @return {@code true} if the termination grace period elapsed, otherwise {@code false}.
     */
    public boolean isTerminationGracePeriodElapsed(final Instant now) {
        Duration terminationGracePeriod = this.config.terminationGracePeriod();
        return this.updatedAt().plus(terminationGracePeriod).isBefore(now);
    }

    /**
     * A timestamped event value.
     *
     * @param ts    The instant of this event.
     * @param value The value of this event.
     * @param type  The type of this event.
     * @param state The service state during this event.
     */
    public record TimestampedEvent(Instant ts, String value, String type, ServiceState state) {
    }

    /**
     * Static helper method for grouping all services instanced by a given property.
     * <p>
     * This method will filter all services instance not having the expected property.
     *
     * @param property The property to group by.
     * @return  The {@link ServiceInstance} grouped by the given property value.
     */
    public static Map<String, List<ServiceInstance>> groupByProperty(final Collection<ServiceInstance> instances, final String property) {
        return instances.stream()
            .filter(it -> Optional.ofNullable(it.props()).map(map -> map.get(property)).isPresent())
            .collect(Collectors.groupingBy(it -> (String) it.props().get(property)));
    }
}
