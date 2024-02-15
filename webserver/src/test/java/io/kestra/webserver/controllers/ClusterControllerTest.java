package io.kestra.webserver.controllers;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceInstanceFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;

class ClusterControllerTest extends JdbcH2ControllerTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ServiceInstanceRepositoryInterface jdbcWorkerInstanceRepository;

    @Inject
    ServiceInstanceFactory serviceInstanceFactory;

    @BeforeEach
    protected void init() {
        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void list() {
        Service service = new Service() {
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
                return ServiceState.CREATED;
            }
        };
        ServiceInstance instance = serviceInstanceFactory.newServiceInstance(service, Map.of());

        jdbcWorkerInstanceRepository.save(instance);

        List<ServiceInstance> find = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/cluster/services"),
            Argument.of(List.class, ServiceInstance.class)
        );

        Assertions.assertFalse(find.isEmpty());
        Assertions.assertTrue(find.stream().anyMatch(it -> it.id().equalsIgnoreCase(instance.id())));
    }
}
