package io.kestra.jdbc.server;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstanceFactory;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.LocalServiceStateFactory;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;

import static io.kestra.core.server.ServiceLivenessManagerTest.newServiceForState;
import static io.kestra.core.server.ServiceLivenessManagerTest.serviceInstanceFor;
import static io.kestra.core.server.ServiceStateTransition.Result.SUCCEED;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class JdbcServiceLivenessManagerTest {

    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(5);

    @Mock
    public ServiceInstanceRepositoryInterface repository;

    private JdbcServiceLivenessManager serviceLivenessManager;

    @Mock
    private KestraContext context;

    @BeforeEach
    void beforeEach() {
        Mockito.when(context.getServerType()).thenReturn(ServerType.WORKER);
        Mockito.when(context.getVersion()).thenReturn("");
        KestraContext.setContext(context);
        ServerConfig config = new ServerConfig(
            Duration.ZERO,
            WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
            new ServerConfig.Liveness(
                true,
                Duration.ZERO,
                DEFAULT_DURATION, // timeout
                DEFAULT_DURATION,
                DEFAULT_DURATION
            )
        );

        this.serviceLivenessManager = new JdbcServiceLivenessManager(
            config,
            new ServiceRegistry(),
            new LocalServiceStateFactory(config, null),
            new ServerInstanceFactory(context, null),
            repository
        );
    }

    @Test
    void shouldRunOnStateTransitionFailureWhenTimeoutForWorker() {
        // Given
        Service running = newServiceForState(Service.ServiceState.RUNNING);
        serviceLivenessManager.updateServiceInstance(running, serviceInstanceFor(running));

        // When
        Instant now = Instant.now();
        final ServiceStateTransition.Response response = new ServiceStateTransition.Response(
            SUCCEED,
            serviceInstanceFor(running)
        );

        Mockito.when(repository.mayTransitionServiceTo(any(ServiceInstance.class), any(Service.ServiceState.class))).thenReturn(response);
        serviceLivenessManager.run(now); // SUCCEED

        // Simulate exception on each transition
        Mockito.when(repository.mayTransitionServiceTo(any(ServiceInstance.class), any(Service.ServiceState.class))).thenThrow(new RuntimeException());

        serviceLivenessManager.run(now.plus(Duration.ofSeconds(2))); // FAIL
        Mockito.verify(context, Mockito.never()).shutdown();

        serviceLivenessManager.run(now.plus(Duration.ofSeconds(4))); // FAIL
        Mockito.verify(context, Mockito.never()).shutdown();

        // Then
        serviceLivenessManager.run(now.plus(Duration.ofSeconds(6))); // TIMEOUT
        Mockito.verify(context, Mockito.times(1)).shutdown();
    }
}