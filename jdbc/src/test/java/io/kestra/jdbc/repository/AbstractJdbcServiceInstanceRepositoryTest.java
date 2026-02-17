package io.kestra.jdbc.repository;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.server.*;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Network;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.kestra.core.server.ServiceStateTransition.Result.FAILED;
import static io.kestra.core.server.ServiceStateTransition.Result.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
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
        repository.update(instance);

        // Then
        Optional<ServiceInstance> result = repository.findById(instance.uid());
        Assertions.assertEquals(Optional.of(instance), result);
    }

    @Test
    protected void shouldDeleteGivenServiceInstance() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::update);
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
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::update);
        String uuid = AbstractJdbcServiceInstanceRepositoryTest.Fixtures.EmptyServiceInstance.uid();

        // When
        Optional<ServiceInstance> result = repository.findById(uuid);

        // Then
        Assertions.assertEquals(Optional.of(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.EmptyServiceInstance), result);
    }

    @Test
    protected void shouldFindAllServiceInstances() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::update);

        // When
        List<ServiceInstance> results = repository.findAll();

        // Then
        assertEquals(results.size(), AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().size());
        assertThat(results).containsExactlyInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().toArray(ServiceInstance[]::new));
    }

    @Test
    protected void shouldFindAllNonRunningInstances() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::update);

        // When
        List<ServiceInstance> results = repository.findAllNonRunningInstances();

        // Then
        assertEquals(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allNonRunning().size(), results.size());
        assertThat(results).containsExactlyInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allNonRunning().toArray(ServiceInstance[]::new));
    }

    @Test
    protected void shouldFindAllInstancesInNotRunningState() {
        // Given
        AbstractJdbcServiceInstanceRepositoryTest.Fixtures.all().forEach(repository::update);

        // When
        List<ServiceInstance> results = repository.findAllInstancesInNotRunningState();

        // Then
        assertEquals(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allInNotRunningState().size(), results.size());
        assertThat(results).containsExactlyInAnyOrder(AbstractJdbcServiceInstanceRepositoryTest.Fixtures.allInNotRunningState().toArray(ServiceInstance[]::new));
    }

    @Test
    void shouldReturnEmptyForTransitionWorkerStateGivenInvalidWorker() {
        // Given
        ServiceInstance instance = Fixtures.RunningServiceInstance;

        // When
        ServiceStateTransition.Response result = repository
            .update(instance, Service.ServiceState.TERMINATING);

        // Then
        Assertions.assertEquals(new ServiceStateTransition.Response(ServiceStateTransition.Result.ABORTED), result);
    }

    @Test
    void shouldReturnSucceedTransitionResponseForValidTransition() {
        // Given
        ServiceInstance instance = Fixtures.RunningServiceInstance;
        repository.update(instance);

        // When
        ServiceStateTransition.Response response = repository
            .update(instance, Service.ServiceState.TERMINATING); // RUNNING -> TERMINATING: valid transition

        // Then
        Assertions.assertEquals(SUCCEEDED, response.result());
        Assertions.assertEquals(Service.ServiceState.TERMINATING, response.instance().state());
        Assertions.assertTrue(response.instance().updatedAt().isAfter(instance.updatedAt()));
    }

    @Test
    void shouldReturnInvalidTransitionResponseForInvalidTransition() {
        // Given
        ServiceInstance instance = Fixtures.EmptyServiceInstance;
        repository.update(instance);

        // When
        ServiceStateTransition.Response response = repository
            .update(instance, Service.ServiceState.RUNNING); // EMPTY -> RUNNING: INVALID transition

        // Then
        Assertions.assertEquals(new ServiceStateTransition.Response(FAILED, instance), response);
    }

    @Test
    void shouldPurgeServiceInstance() {
        // Given
        ServiceInstance instance = Fixtures.RunningServiceInstance;
        repository.update(instance);
        instance = Fixtures.EmptyServiceInstance;
        repository.update(instance);

        // When
        int purged = repository.purgeEmptyInstances(Instant.now());

        //Then
        assertThat(purged).isEqualTo(1);
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
            serviceInstanceFor(Service.ServiceState.INACTIVE);

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
                ServiceType.WORKER,
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
