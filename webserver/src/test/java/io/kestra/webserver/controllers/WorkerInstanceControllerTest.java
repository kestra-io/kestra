package io.kestra.webserver.controllers;

import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WorkerInstanceControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcWorkerInstanceRepository jdbcWorkerInstanceRepository;

    @BeforeEach
    protected void init() {
        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void list() {
        WorkerInstance workerInstance =  WorkerInstance.builder()
            .workerUuid(UUID.randomUUID())
            .workerGroup(null)
            .managementPort(0)
            .hostname("kestra.io")
            .partitions(null)
            .port(0)
            .status(WorkerInstance.Status.UP)
            .build();


        jdbcWorkerInstanceRepository.save(workerInstance);

        List<WorkerInstance> find = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/workers"), Argument.of(List.class, WorkerInstance.class));
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getWorkerUuid(), is(workerInstance.getWorkerUuid()));
    }

}
