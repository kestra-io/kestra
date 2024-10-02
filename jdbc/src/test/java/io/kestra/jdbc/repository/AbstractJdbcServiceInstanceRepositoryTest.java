package io.kestra.jdbc.repository;

import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceStateTransition;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Network;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.kestra.core.server.ServiceStateTransition.Result.FAILED;
import static io.kestra.core.server.ServiceStateTransition.Result.SUCCEED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
public abstract class AbstractJdbcServiceInstanceRepositoryTest {

    @Inject
    protected AbstractJdbcServiceInstanceRepository repository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    protected void shouldSaveServiceInstance() {
        // Given
        ServiceInstance instance = AbstractJdbcServiceInstanceRepositoryTest.Fixtures.RunningServiceInstance;

        // When
        repository.save(instance);

        // Then
        Optional<ServiceInstance> result = repository.findById(instance.uid());
        Assertions.assertEquals(Optional.of(instance), result);
    }

    @Test
    protected void shouldDeleteGivenServiceInstance() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::save);
        final ServiceInstance instance = AbstractJdbcServiceInstanceRepositoryTest.Fixtures.EmptyServiceInstance;

        // When
        repository.delete(instance);

        // Then
        Optional<ServiceInstance> result = repository.findById(instance.uid());
        Assertions.assertEquals(Optional.empty(), result);
    }

    @Test
    protected void shouldFindByServiceId() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::save);
        String uuid = AbstractJdbcServiceInstanceRepositoryTest.Fixtures.EmptyServiceInstance.uid();

        // When
        Optional<ServiceInstance> result = repository.findById(uuid);

        // Then
        Assertions.assertEquals(Optional.of(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.EmptyServiceInstance), result);
    }

    @Test
    protected void shouldFindAllServiceInstances() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::save);

        // When
        List<ServiceInstance> results = repository.findAll();

        // Then
        assertEquals(results.size(), AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().size());
        assertThat(results, Matchers.containsInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().toArray()));
    }

    @Test
    protected void shouldFindAllNonRunningInstances() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::save);

        // When
        List<ServiceInstance> results = repository.findAllNonRunningInstances();

        // Then
        assertEquals(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allNonRunning().size(), results.size());
        assertThat(results, Matchers.containsInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allNonRunning().toArray()));
    }

    @Test
    protected void shouldFindAllInstancesInNotRunningState() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::save);

        // When
        List<ServiceInstance> results = repository.findAllInstancesInNotRunningState();

        // Then
        assertEquals(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allInNotRunningState().size(), results.size());
        assertThat(results, Matchers.containsInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allInNotRunningState().toArray()));
    }

    @Test
    void shouldReturnEmptyForTransitionWorkerStateGivenInvalidWorker() {
        // Given
        ServiceInstance instance = Fixtures.RunningServiceInstance;

        // When
        ServiceStateTransition.Response result = repository
            .mayTransitionServiceTo(instance, Service.ServiceState.TERMINATING);

        // Then
        Assertions.assertEquals(new ServiceStateTransition.Response(ServiceStateTransition.Result.ABORTED), result);
    }

    @Test
    void shouldReturnSucceedTransitionResponseForValidTransition() {
        // Given
        ServiceInstance instance = Fixtures.RunningServiceInstance;
        repository.save(instance);

        // When
        ServiceStateTransition.Response response = repository
            .mayTransitionServiceTo(instance, Service.ServiceState.TERMINATING); // RUNNING -> TERMINATING: valid transition

        // Then
        Assertions.assertEquals(SUCCEED, response.result());
        Assertions.assertEquals(Service.ServiceState.TERMINATING, response.instance().state());
        Assertions.assertTrue(response.instance().updatedAt().isAfter(instance.updatedAt()));
    }

    @Test
    void shouldReturnInvalidTransitionResponseForInvalidTransition() {
        // Given
        ServiceInstance instance = Fixtures.EmptyServiceInstance;
        repository.save(instance);

        // When
        ServiceStateTransition.Response response = repository
            .mayTransitionServiceTo(instance, Service.ServiceState.RUNNING); // EMPTY -> RUNNING: INVALID transition

        // Then
        Assertions.assertEquals(new ServiceStateTransition.Response(FAILED, instance), response);
    }

    public static final class Fixtures {

        public static List<ServiceInstance> all() {
            return List.of(
                RunningServiceInstance,
                PendingShutdownServiceInstance,
                GracefulShutdownServiceInstance,
                ForcedShutdownServiceInstance,
                NotRunningServiceInstance,
                EmptyServiceInstance
            );
        }

        public static List<ServiceInstance> allNonRunning() {
            return List.of(
                PendingShutdownServiceInstance,
                GracefulShutdownServiceInstance,
                ForcedShutdownServiceInstance,
                NotRunningServiceInstance,
                EmptyServiceInstance
            );
        }

        public static List<ServiceInstance> allInNotRunningState() {
            return List.of(NotRunningServiceInstance);
        }

        public static final ServiceInstance RunningServiceInstance =
            serviceInstanceFor(Service.ServiceState.RUNNING);

        public static final ServiceInstance PendingShutdownServiceInstance =
            serviceInstanceFor(Service.ServiceState.TERMINATING);

        public static final ServiceInstance GracefulShutdownServiceInstance =
            serviceInstanceFor(Service.ServiceState.TERMINATED_GRACEFULLY);

        public static final ServiceInstance ForcedShutdownServiceInstance =
            serviceInstanceFor(Service.ServiceState.TERMINATED_FORCED);

        public static final ServiceInstance NotRunningServiceInstance =
            serviceInstanceFor(Service.ServiceState.NOT_RUNNING);

        public static final ServiceInstance EmptyServiceInstance =
            serviceInstanceFor(Service.ServiceState.EMPTY);

        public static ServiceInstance serviceInstanceFor(final Service.ServiceState state) {
            ServerConfig config = new ServerConfig(
                Duration.ZERO,
                WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
                new ServerConfig.Liveness(
                    true,
                    Duration.ZERO,
                    Duration.ofSeconds(10), // timeout
                    Duration.ZERO,
                    Duration.ZERO
                )
            );
            return new ServiceInstance(
                IdUtils.create(),
                Service.ServiceType.WORKER,
                state,
                new ServerInstance(
                    ServerInstance.Type.STANDALONE,
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
}