package io.kestra.webserver.controllers.api;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowForExecution;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@KestraTest(startRunner = true)
class ExecutionRunnerControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @SneakyThrows
    @BeforeEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(MAIN_TENANT, repositoryLoader);
    }

    @Test
    void webhookWithOutputs() {
        Map<String, Object> outputs = client.toBlocking().retrieve(
            GET(
                "/api/v1/main/executions/webhook/" + ExecutionControllerTest.TESTS_FLOW_NS + "/webhook-outputs/webhook-outputs"
            ),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat(outputs).hasFieldOrPropertyWithValue("status", "ok");
        assertThat(outputs).containsKey("executionId");
    }
}
