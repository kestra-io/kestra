package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.IdUtils;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import lombok.Builder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.IN)
                .value(List.of(Service.ServiceState.RUNNING.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(runningInstance, terminatingInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.IN)
                .value(List.of(Service.ServiceState.RUNNING.name(), Service.ServiceState.TERMINATING.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(inactiveInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.IN)
                .value(List.of(Service.ServiceState.INACTIVE.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, workerInstance))
            .expectedInstances(List.of(runningInstance, workerInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.TYPE)
                .operation(QueryFilter.Op.IN)
                .value(List.of(ServiceType.WORKER.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.CREATED)
                .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                .value(Instant.now().minusSeconds(60).toString())
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of())
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.CREATED)
                .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                .value(Instant.now().plusSeconds(60).toString())
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, terminatingInstance, inactiveInstance))
            .expectedInstances(List.of(terminatingInstance, inactiveInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.NOT_IN)
                .value(List.of(Service.ServiceState.RUNNING.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance, workerInstance, schedulerInstance))
            .expectedInstances(List.of(schedulerInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.TYPE)
                .operation(QueryFilter.Op.NOT_IN)
                .value(List.of(ServiceType.WORKER.name()))
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of(runningInstance))
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.CREATED)
                .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                .value(Instant.now().toString())
                .build())
            .build(),

        FilterTestCase.builder()
            .instances(List.of(runningInstance))
            .expectedInstances(List.of())
            .filter(QueryFilter.builder()
                .field(QueryFilter.Field.CREATED)
                .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                .value(Instant.now().minusSeconds(60).toString())
                .build())
            .build()
    );

    @ParameterizedTest
    @FieldSource("filterTestCases")
    void shouldFindServiceInstancesByFilter(FilterTestCase testCase) {
        // Given
        testCase.instances().forEach(serviceInstance ->
                serviceInstanceRepository.save(serviceInstance)
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

    @Builder
    protected record FilterTestCase(
        List<ServiceInstance> instances,
        List<ServiceInstance> expectedInstances,
        QueryFilter filter
    ) {}

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
            new ServerConfig(Duration.ofSeconds(5), null, null),
            null,
            null,
            0L
        );
    }
}
