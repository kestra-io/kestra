package io.kestra.core.server;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Network;

import static io.kestra.core.server.ServiceStateTransition.Result.ABORTED;
import static io.kestra.core.server.ServiceStateTransition.Result.FAILED;
import static io.kestra.core.server.ServiceStateTransition.Result.SUCCEEDED;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.LENIENT)
public class ServiceLivenessManagerTest {

    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(5);

    @Mock
    public ServiceLivenessUpdater serviceLivenessUpdater;

    @Captor
    ArgumentCaptor<ServiceInstance> workerInstanceCaptor;

    private ServiceLivenessManager serviceLivenessManager;
    private ServiceLivenessListener livenessListener;

    @Mock
    private ServiceLivenessManager.OnStateTransitionFailureCallback onStateTransitionFailureCallback;

    @BeforeEach
    void beforeEach() {
        KestraContext kestraContext = Mockito.mock(KestraContext.class);
        ServerConfig config = new ServerConfig(
            Duration.ZERO,
            WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                DEFAULT_DURATION, // timeout
                DEFAULT_DURATION,
                DEFAULT_DURATION
            ),
            null,
            null,
            null
        );

        KestraContext context = Mockito.mock(KestraContext.class);
        KestraContext.setContext(context);
        when(context.getServerType()).thenReturn(ServerType.INDEXER);
        this.livenessListener = Mockito.mock(ServiceLivenessListener.class);
        this.serviceLivenessManager = new ServiceLivenessManager(
            config,
            new ServiceRegistry(),
            new LocalServiceStateFactory(config, new ServerInstanceFactory(context, null)),
            new ServerInstanceFactory(kestraContext, null),
            serviceLivenessUpdater,
            onStateTransitionFailureCallback,
            List.of(livenessListener)
        );
    }

    @Test
    void shouldSaveWorkerInstanceOnRunningStateChange() {
        // Given
        Service service = newServiceForState(Service.ServiceState.CREATED);
        final ServiceStateChangeEvent event = new ServiceStateChangeEvent(service);

        // When
        serviceLivenessManager.onServiceStateChangeEvent(event);

        // Then
        Mockito.verify(serviceLivenessUpdater, Mockito.only()).update(workerInstanceCaptor.capture());

        ServiceInstance value = workerInstanceCaptor.getValue();
        Assertions.assertEquals(Service.ServiceState.CREATED, value.state());
        Assertions.assertEquals(value, serviceLivenessManager.allServiceInstances().getFirst());
    }

    @Test
    void shouldUpdateStateOnScheduleForSucceedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        Service terminating = newServiceForState(Service.ServiceState.TERMINATING);
        ServiceInstance instance = serviceInstanceFor(terminating);
        final ServiceStateTransition.Response response = new ServiceStateTransition.Response(
            SUCCEEDED,
            instance
        );

        // mock the state transition result
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(response);

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Assertions.assertEquals(instance, serviceLivenessManager.allServiceInstances().getFirst());
        Mockito.verify(onStateTransitionFailureCallback, Mockito.never())
            .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true));
    }

    @Test
    void shouldRunOnStateTransitionFailureForFailedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        Service disconnecting = newServiceForState(Service.ServiceState.TERMINATING);
        ServiceInstance instance = serviceInstanceFor(disconnecting);
        final ServiceStateTransition.Response response = new ServiceStateTransition.Response(
            FAILED,
            instance
        );

        // mock the state transition result
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(response);

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Assertions.assertEquals(instance, serviceLivenessManager.allServiceInstances().getFirst());
        Mockito.verify(onStateTransitionFailureCallback, Mockito.only())
            .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true));
    }

    @Test
    void shouldNotRunOnStateTransitionFailureForAbortedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        // mock the state transition result
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(ABORTED));

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Mockito.verify(onStateTransitionFailureCallback, Mockito.never())
            .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true));
    }

    @Test
    void shouldNotifyLivenessListenerOnScheduledSuccess() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        ServiceInstance updated = serviceInstanceFor(running);
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(SUCCEEDED, updated));

        // When
        Instant now = Instant.now();
        serviceLivenessManager.onSchedule(now);

        // Then
        verify(livenessListener).onLivenessUpdate(
            Mockito.eq(now),
            Mockito.any(ServiceInstance.class),
            Mockito.eq(Service.ServiceState.RUNNING)
        );
    }

    @Test
    void shouldNotifyLivenessListenerOnRecoveredAbortedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(ABORTED));

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then — ABORTED is recovered and treated as success
        verify(livenessListener).onLivenessUpdate(
            Mockito.any(Instant.class),
            Mockito.any(ServiceInstance.class),
            Mockito.any(Service.ServiceState.class)
        );
    }

    @Test
    void shouldNotNotifyLivenessListenerOnFailedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(FAILED, serviceInstanceFor(running)));

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        verifyNoInteractions(livenessListener);
    }

    @Test
    void shouldSwallowLivenessListenerException() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        ServiceInstance updated = serviceInstanceFor(running);
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(SUCCEEDED, updated));

        Mockito.doThrow(new RuntimeException("listener-boom"))
            .when(livenessListener).onLivenessUpdate(Mockito.any(), Mockito.any(), Mockito.any());

        // When / Then — exception must not propagate
        serviceLivenessManager.onSchedule(Instant.now());

        verify(livenessListener).onLivenessUpdate(Mockito.any(), Mockito.any(), Mockito.any());
        Assertions.assertEquals(updated, serviceLivenessManager.allServiceInstances().getFirst());
    }

    @Test
    void shouldRegisterNewInstanceAfterPreviousOneTerminated() {
        // Given - a service that completed a terminal state transition (isStateUpdatable = false)
        Service terminating = newServiceForState(Service.ServiceState.TERMINATING);
        serviceLivenessManager.updateServiceInstance(terminating, serviceInstanceFor(terminating));

        Service terminated = newServiceForState(Service.ServiceState.TERMINATED_FORCED);
        ServiceInstance terminatedInstance = serviceInstanceFor(terminated);
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(SUCCEEDED, terminatedInstance));
        serviceLivenessManager.onServiceStateChangeEvent(new ServiceStateChangeEvent(terminated));
        // At this point isStateUpdatable is false for ServiceType.WORKER

        // When - a CREATED event arrives for a new service of the same type (new worker restarts)
        Mockito.clearInvocations(serviceLivenessUpdater);
        Service newWorker = newServiceForState(Service.ServiceState.CREATED);
        serviceLivenessManager.onServiceStateChangeEvent(new ServiceStateChangeEvent(newWorker));

        // Then - the new CREATED instance is persisted to the DB
        Mockito.verify(serviceLivenessUpdater, Mockito.atLeastOnce()).update(workerInstanceCaptor.capture());
        Assertions.assertEquals(Service.ServiceState.CREATED, workerInstanceCaptor.getValue().state());

        // And - the heartbeat resumes for the new instance (isStateUpdatable reset to true)
        Mockito.clearInvocations(serviceLivenessUpdater);
        ServiceInstance newInstance = serviceLivenessManager.allServiceInstances().getFirst();
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(SUCCEEDED, newInstance));
        serviceLivenessManager.onSchedule(Instant.now());
        Mockito.verify(serviceLivenessUpdater, Mockito.atLeastOnce())
            .update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class));
    }

    @Test
    void shouldDisableHeartbeatAfterTerminalStateTransition() {
        // Given - a service in TERMINATING state
        Service terminating = newServiceForState(Service.ServiceState.TERMINATING);
        serviceLivenessManager.updateServiceInstance(terminating, serviceInstanceFor(terminating));

        Service terminated = newServiceForState(Service.ServiceState.TERMINATED_FORCED);
        ServiceInstance terminatedInstance = serviceInstanceFor(terminated);
        when(serviceLivenessUpdater.update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(SUCCEEDED, terminatedInstance));

        // When - the service transitions to TERMINATED_FORCED
        serviceLivenessManager.onServiceStateChangeEvent(new ServiceStateChangeEvent(terminated));

        // Then - the heartbeat should no longer fire for this service
        Mockito.clearInvocations(serviceLivenessUpdater);
        serviceLivenessManager.onSchedule(Instant.now());
        Mockito.verify(serviceLivenessUpdater, Mockito.never())
            .update(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class));
    }

    public static Service newServiceForState(final Service.ServiceState state) {
        return new Service() {

            private final String id = IdUtils.create();

            @Override
            public String getId() {
                return id;
            }

            @Override
            public ServiceType getType() {
                return ServiceType.WORKER;
            }

            @Override
            public ServiceState getState() {
                return state;
            }
        };
    }

    public static ServiceInstance serviceInstanceFor(final Service service) {
        ServerConfig config = new ServerConfig(
            Duration.ZERO,
            WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                Duration.ofSeconds(10), // timeout
                Duration.ZERO,
                Duration.ZERO
            ),
            null,
            null,
            null
        );
        return new ServiceInstance(
            service.getId(),
            service.getType(),
            service.getState(),
            new ServerInstance(
                ServerInstance.Type.SERVER,
                "N/A",
                Network.localHostname(),
                Map.of(),
                Set.of()
            ),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            List.of(),
            config,
            Map.of(),
            Set.of()
        );
    }
}