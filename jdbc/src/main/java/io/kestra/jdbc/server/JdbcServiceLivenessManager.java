package io.kestra.jdbc.server;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstanceFactory;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.LocalServiceStateFactory;
import io.kestra.core.server.ServiceLivenessUpdater;
import io.kestra.core.server.ServiceLivenessManager;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;
import io.kestra.jdbc.runner.JdbcRunnerEnabled;
import io.micronaut.context.annotation.Context;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Context
@JdbcRunnerEnabled
@JdbcRepositoryEnabled
public final class JdbcServiceLivenessManager extends ServiceLivenessManager {

    private static final Logger log = LoggerFactory.getLogger(JdbcServiceLivenessManager.class);

    @Inject
    public JdbcServiceLivenessManager(final ServerConfig configuration,
                                      final ServiceRegistry registry,
                                      final LocalServiceStateFactory localServiceStateFactory,
                                      final ServerInstanceFactory serverInstanceFactory,
                                      final ServiceLivenessUpdater serviceLivenessUpdater) {
        super(
            configuration,
            registry,
            localServiceStateFactory,
            serverInstanceFactory,
            serviceLivenessUpdater,
            new DefaultStateTransitionFailureCallback()
        );
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    @EventListener
    public void onServiceStateChangeEvent(final ServiceStateChangeEvent event) {
        super.onServiceStateChangeEvent(event);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected boolean beforeScheduledStateUpdate(final Instant now,
                                                 final Service service,
                                                 final ServiceInstance instance) {

        // Proactively disconnect a WORKER server when it fails to update its current state
        // for more than the configured liveness timeout (this is to prevent zombie server).
        if (isLivenessEnabled() && isWorkerServer() && isServerDisconnected(now)) {
            log.error("[Service id={}, type='{}', hostname='{}'] Failed to update state before reaching timeout ({}ms). Disconnecting.",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                getElapsedMilliSinceLastStateUpdate(now)
            );
            // Force the WORKER to transition to DISCONNECTED.
            ServiceInstance updated = updateServiceInstanceState(now, service, Service.ServiceState.DISCONNECTED, OnStateTransitionFailureCallback.NOOP);
            if (updated != null) {
                // Trigger state transition failure callback.
                onStateTransitionFailureCallback.execute(now, service, updated, true);
            }
            return false;
        }

        return true;
    }

    /**
     * Checks whether the current server is running in WORKER mode.
     */
    private boolean isWorkerServer() {
        return KestraContext.getContext().getServerType().equals(ServerType.WORKER);
    }

    /**
     * Checks whether the server is DISCONNECTED.
     */
    private boolean isServerDisconnected(final Instant now) {
        long timeoutMilli = serverConfig.liveness().timeout().toMillis();
        // Check thread starvation or clock leap (i.e., JVM was frozen)
        return getElapsedMilliSinceLastSchedule(now) < timeoutMilli && getElapsedMilliSinceLastStateUpdate(now) > timeoutMilli;
    }

    private long getElapsedMilliSinceLastStateUpdate(final Instant now) {
        return now.toEpochMilli() - (lastSucceedStateUpdated() != null ? lastSucceedStateUpdated().toEpochMilli() : now.toEpochMilli());
    }
}
