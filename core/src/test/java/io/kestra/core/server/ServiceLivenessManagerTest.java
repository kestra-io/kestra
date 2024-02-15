package io.kestra.core.server;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Network;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static io.kestra.core.server.ServiceStateTransition.Result.ABORTED;
import static io.kestra.core.server.ServiceStateTransition.Result.FAILED;
import static io.kestra.core.server.ServiceStateTransition.Result.SUCCEED;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class ServiceLivenessManagerTest {

    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(5);

    @Mock
    public ServiceInstanceRepositoryInterface repository;

    @Captor
    ArgumentCaptor<ServiceInstance> workerInstanceCaptor;

    private ServiceLivenessManager serviceLivenessManager;

    @Mock
    private ServiceLivenessManager.OnStateTransitionFailureCallback onStateTransitionFailureCallback;

    @BeforeEach
    void beforeEach() {
        ServerConfig config = new ServerConfig(Duration.ZERO,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                DEFAULT_DURATION, // timeout
                DEFAULT_DURATION,
                DEFAULT_DURATION
            )
        );

        this.serviceLivenessManager = new ServiceLivenessManager(
            config,
            new ServiceRegistry(),
            new ServiceInstanceFactory(config, null, null),
            repository,
            onStateTransitionFailureCallback
        );
    }

    @Test
    void shouldSaveWorkerInstanceOnRunningStateChange() {
        // Given
        Service service = newServiceForState(Service.ServiceState.CREATED);
        ServiceInstance instance = serviceInstanceFor(service);
        final ServiceStateChangeEvent event = new ServiceStateChangeEvent(service);
        Mockito.when(repository.save(Mockito.any(ServiceInstance.class))).thenReturn(instance);

        // When
        serviceLivenessManager.onServiceStateChangeEvent(event);

        // Then
        Mockito.verify(repository, Mockito.only()).save(workerInstanceCaptor.capture());

        ServiceInstance value = workerInstanceCaptor.getValue();
        Assertions.assertEquals(Service.ServiceState.CREATED, value.state());
        Assertions.assertEquals(instance, serviceLivenessManager.allServiceInstances().get(0));
    }

    @Test
    void shouldUpdateStateOnScheduleForSucceedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        Service terminating = newServiceForState(Service.ServiceState.TERMINATING);
        ServiceInstance instance = serviceInstanceFor(terminating);
        final ServiceStateTransition.Response response = new ServiceStateTransition.Response(
            SUCCEED,
            instance
        );

        // mock the state transition result
        Mockito
            .when(repository.mayTransitionServiceTo(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(response);

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Assertions.assertEquals(instance, serviceLivenessManager.allServiceInstances().get(0));
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
        Mockito
            .when(repository.mayTransitionServiceTo(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(response);

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Assertions.assertEquals(instance, serviceLivenessManager.allServiceInstances().get(0));
        Mockito.verify(onStateTransitionFailureCallback, Mockito.only())
            .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true));
    }

    @Test
    void shouldNotRunOnStateTransitionFailureForAbortedTransition() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        // mock the state transition result
        Mockito
            .when(repository.mayTransitionServiceTo(Mockito.any(ServiceInstance.class), Mockito.any(Service.ServiceState.class)))
            .thenReturn(new ServiceStateTransition.Response(ABORTED));

        // When
        serviceLivenessManager.onSchedule(Instant.now());

        // Then
        Mockito.verify(onStateTransitionFailureCallback, Mockito.never())
            .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true));
    }

    public static Service newServiceForState(final Service.ServiceState state) {
        return new Service() {
            @Override
            public String getId() {
                return IdUtils.create();
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
        ServerConfig config = new ServerConfig(Duration.ZERO,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                Duration.ofSeconds(10), // timeout
                Duration.ZERO,
                Duration.ZERO
            )
        );
        return new ServiceInstance(
            service.getId(),
            service.getType(),
            service.getState(),
            new ServerInstance(
                ServerInstance.Type.SERVER,
                "N/A",
                Network.localHostname(), Map.of()
            ),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            List.of(),
            config,
            Map.of()
        );
    }
}