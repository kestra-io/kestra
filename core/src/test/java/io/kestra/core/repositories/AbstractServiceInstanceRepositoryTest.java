package io.kestra.core.repositories;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.IdUtils;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import lombok.Builder;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractServiceInstanceRepositoryTest {
    @Inject
    ServiceInstanceRepositoryInterface serviceInstanceRepository;

    protected static final ServiceInstance runningInstance = newServiceInstance(Service.ServiceState.RUNNING);
    protected static final ServiceInstance terminatingInstance = newServiceInstance(Service.ServiceState.TERMINATING);
    protected static final ServiceInstance inactiveInstance = newServiceInstance(Service.ServiceState.INACTIVE);
    protected static final ServiceInstance workerInstance = newServiceInstance(Service.ServiceState.RUNNING);
    protected static final ServiceInstance schedulerInstance = newServiceInstance(Service.ServiceState.RUNNING, ServiceType.SCHEDULER);

    protected static final List<FilterTestCase> filterTestCases = List.of(
        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.STATE)
                    .operation(QueryFilter.Op.IN)
                    .value(List.of(Service.ServiceState.RUNNING.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(runningInstance, terminatingInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.STATE)
                    .operation(QueryFilter.Op.IN)
                    .value(List.of(Service.ServiceState.RUNNING.name(), Service.ServiceState.TERMINATING.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(inactiveInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.STATE)
                    .operation(QueryFilter.Op.IN)
                    .value(List.of(Service.ServiceState.INACTIVE.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, workerInstance))
            .expectedInstances(List.of(runningInstance, workerInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TYPE)
                    .operation(QueryFilter.Op.IN)
                    .value(List.of(ServiceType.WORKER.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.CREATED)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Instant.now().minusSeconds(60).toString())
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of())
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.CREATED)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Instant.now().plusSeconds(60).toString())
                    .build()
            )
            .build(),

        // ISO-8601 duration: "created in the last 24h" → now-created instance matches
        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.CREATED)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value("PT24H")
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(terminatingInstance, inactiveInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.STATE)
                    .operation(QueryFilter.Op.NOT_IN)
                    .value(List.of(Service.ServiceState.RUNNING.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, workerInstance, schedulerInstance))
            .expectedInstances(List.of(schedulerInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TYPE)
                    .operation(QueryFilter.Op.NOT_IN)
                    .value(List.of(ServiceType.WORKER.name()))
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.CREATED)
                    .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                    .value(Instant.now().toString())
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of())
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.CREATED)
                    .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                    .value(Instant.now().minusSeconds(60).toString())
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, schedulerInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.QUERY)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(runningInstance.uid())
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, schedulerInstance))
            .expectedInstances(List.of(schedulerInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.QUERY)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("scheduler")
                    .build()
            )
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, schedulerInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.QUERY)
                    .operation(QueryFilter.Op.NOT_EQUALS)
                    .value("scheduler")
                    .build()
            )
            .build(),

        // QUERY matches on the server hostname (all fixtures run on "localhost")
        FilterTestCase.builder()
            .instances(List.of(runningInstance, schedulerInstance))
            .expectedInstances(List.of(runningInstance, schedulerInstance))
            .filter(
                QueryFilter.builder()
                    .field(QueryFilter.Field.QUERY)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("localhost")
                    .build()
            )
            .build()
    );

    @ParameterizedTest
    @FieldSource("filterTestCases")
    void shouldFindServiceInstancesByFilter(FilterTestCase testCase) {
        // Given
        testCase.instances().forEach(
            serviceInstance -> serviceInstanceRepository.save(serviceInstance)
        );

        // When
        ArrayListTotal<ServiceInstance> results = serviceInstanceRepository.find(
            Pageable.unpaged(), List.of(testCase.filter())
        );

        // Then
        assertThat(results)
            .usingRecursiveFieldByFieldElementComparatorOnFields("uid")
            .containsExactlyInAnyOrderElementsOf(testCase.expectedInstances());
    }

    @Test
    public void shouldFindById() {
        ServiceInstance instance = newServiceInstance(Service.ServiceState.RUNNING);
        serviceInstanceRepository.save(instance);

        Optional<ServiceInstance> found = serviceInstanceRepository.findById(instance.uid());

        assertThat(found).isPresent();
        assertThat(found.get().uid()).isEqualTo(instance.uid());
    }

    @Test
    public void shouldReturnEmptyForUnknownId() {
        Optional<ServiceInstance> found = serviceInstanceRepository.findById(IdUtils.create());
        assertThat(found).isEmpty();
    }

    @Test
    public void shouldFindAll() {
        ServiceInstance instance1 = newServiceInstance(Service.ServiceState.RUNNING);
        ServiceInstance instance2 = newServiceInstance(Service.ServiceState.TERMINATING);

        serviceInstanceRepository.save(instance1);
        serviceInstanceRepository.save(instance2);

        List<ServiceInstance> all = serviceInstanceRepository.findAll();

        assertThat(all.stream().map(ServiceInstance::uid).toList())
            .contains(instance1.uid(), instance2.uid());
    }

    @Test
    public void shouldDeleteServiceInstance() {
        ServiceInstance instance = newServiceInstance(Service.ServiceState.RUNNING);
        serviceInstanceRepository.save(instance);

        assertThat(serviceInstanceRepository.findById(instance.uid())).isPresent();

        serviceInstanceRepository.delete(instance);

        assertThat(serviceInstanceRepository.findById(instance.uid())).isEmpty();
    }

    @Test
    public void shouldFindWithStatesAndTypesDefaultMethod() {
        ServiceInstance workerRunning = newServiceInstance(Service.ServiceState.RUNNING, ServiceType.WORKER);
        ServiceInstance schedulerRunning = newServiceInstance(Service.ServiceState.RUNNING, ServiceType.SCHEDULER);
        ServiceInstance workerTerminating = newServiceInstance(Service.ServiceState.TERMINATING, ServiceType.WORKER);

        serviceInstanceRepository.save(workerRunning);
        serviceInstanceRepository.save(schedulerRunning);
        serviceInstanceRepository.save(workerTerminating);

        // both states and types provided
        ArrayListTotal<ServiceInstance> result = serviceInstanceRepository.find(
            Pageable.unpaged(),
            Set.of(Service.ServiceState.RUNNING),
            Set.of(ServiceType.WORKER)
        );

        assertThat(result.stream().map(ServiceInstance::uid).toList()).containsExactly(workerRunning.uid());
    }

    @Test
    public void shouldFindWithNullStatesAndTypes() {
        ServiceInstance instance = newServiceInstance(Service.ServiceState.RUNNING);
        serviceInstanceRepository.save(instance);

        ArrayListTotal<ServiceInstance> result = serviceInstanceRepository.find(
            Pageable.unpaged(), null, null
        );

        assertThat(result.stream().map(ServiceInstance::uid).toList())
            .contains(instance.uid());
    }

    @Test
    public void shouldFindAllInstancesBetween() {
        Instant from = Instant.now().minus(Duration.ofMinutes(10));
        Instant to = Instant.now().plus(Duration.ofMinutes(10));

        ServiceInstance instance = newServiceInstance(Service.ServiceState.RUNNING, ServiceType.SCHEDULER);
        serviceInstanceRepository.save(instance);

        List<ServiceInstance> results = serviceInstanceRepository.findAllInstancesBetween(
            ServiceType.SCHEDULER, from, to
        );

        assertThat(results.stream().map(ServiceInstance::uid).toList())
            .contains(instance.uid());
    }

    @Test
    public void shouldNotFindInstancesOutsideDateRange() {
        Instant from = Instant.now().plus(Duration.ofMinutes(10));
        Instant to = Instant.now().plus(Duration.ofMinutes(20));

        ServiceInstance instance = newServiceInstance(Service.ServiceState.RUNNING, ServiceType.SCHEDULER);
        serviceInstanceRepository.save(instance);

        List<ServiceInstance> results = serviceInstanceRepository.findAllInstancesBetween(
            ServiceType.SCHEDULER, from, to
        );

        assertThat(results.stream().map(ServiceInstance::uid).toList())
            .doesNotContain(instance.uid());
    }

    @Test
    public void shouldPaginateFindResults() {
        for (int i = 0; i < 5; i++) {
            serviceInstanceRepository.save(newServiceInstance(Service.ServiceState.RUNNING));
        }

        ArrayListTotal<ServiceInstance> page1 = serviceInstanceRepository.find(Pageable.from(1, 2), List.of());
        assertThat(page1).hasSizeLessThanOrEqualTo(2);

        ArrayListTotal<ServiceInstance> allUnpaged = serviceInstanceRepository.find(Pageable.unpaged(), List.of());
        assertThat(page1.getTotal()).isEqualTo(allUnpaged.getTotal());
    }

    @Builder
    protected record FilterTestCase(
        List<ServiceInstance> instances,
        List<ServiceInstance> expectedInstances,
        QueryFilter filter) {
    }

    protected static ServiceInstance newServiceInstance(Service.ServiceState state) {
        return newServiceInstance(state, ServiceType.WORKER);
    }

    protected static ServiceInstance newServiceInstance(Service.ServiceState state, ServiceType type) {
        return new ServiceInstance(
            IdUtils.create(),
            type,
            state,
            new ServerInstance(ServerInstance.Type.STANDALONE, "0.21.0", "localhost", null, null),
            Instant.now().minus(Duration.ofSeconds(30)),
            Instant.now().minus(Duration.ofSeconds(30)),
            null,
            new ServerConfig(Duration.ofSeconds(5), null, null, null, null, null),
            null,
            null,
            0L
        );
    }
}
