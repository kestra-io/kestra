package io.kestra.webserver.controllers.api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowForExecution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@KestraTest
class ExecutionControllerTest {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    public static final String TESTS_FLOW_NS = "io.kestra.tests";
    public static final String TESTS_WEBHOOK_KEY = "a-secret-key";

    @Test
    void getExecutionNotFound() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/exec_id_not_found"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    private MultipartBody createExecutionInputsFlowBody() {
        // Trigger execution
        File applicationFile = new File(
            Objects.requireNonNull(
                ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
            ).getPath()
        );

        File logbackFile = new File(
            Objects.requireNonNull(
                ExecutionControllerTest.class.getClassLoader().getResource("logback.xml")
            ).getPath()
        );

        return MultipartBody.builder()
            .addPart("string", "myString")
            .addPart("int", "42")
            .addPart("float", "42.42")
            .addPart("instant", "2019-10-06T18:27:49Z")
            .addPart("files", "file", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .addPart("files", "optionalFile", MediaType.TEXT_XML_TYPE, logbackFile)
            .addPart("secret", "secret")
            .addPart("array", "[1, 2, 3]")
            .addPart("json", "{}")
            .addPart("yaml", "{}")
            .build();
    }

    @Test
    void webhookFlowNotFound() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/main/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        ImmutableMap.of("a", 1, "b", true)
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).contains("Not Found: Flow not found");

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .PUT(
                        "/api/v1/main/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        Collections.singletonList(ImmutableMap.of("a", 1, "b", true))
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).contains("Not Found: Flow not found");

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/main/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        "bla"
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).contains("Not Found: Flow not found");

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                GET("/api/v1/main/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13"),
                Execution.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).contains("Not Found: Flow not found");

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/main/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        "{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}"
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).contains("Not Found: Flow not found");
    }

    @Test
    @LoadFlows(value = { "flows/valids/webhook-dynamic-key.yaml" })
    void webhookDynamicKey() {
        Execution execution = client.toBlocking().retrieve(
            GET(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-dynamic-key/webhook-dynamic-key"
            ),
            Execution.class
        );

        assertThat(execution).isNotNull();
        assertThat(execution.getId()).isNotNull();
    }

    @Test
    @LoadFlows(value = { "flows/valids/webhook-secret-key.yaml" })
    @EnabledIfEnvironmentVariable(named = "SECRET_WEBHOOK_KEY", matches = ".*")
    void webhookDynamicKeyFromASecret() {
        Execution execution = client.toBlocking().retrieve(
            GET(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-secret-key/secretKey"
            ),
            Execution.class
        );

        assertThat(execution).isNotNull();
        assertThat(execution.getId()).isNotNull();
    }

    @Test
    @LoadFlows(value = { "flows/valids/webhook-with-condition.yaml" })
    void webhookWithCondition() {
        record Hello(String hello) {
        }

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                    new Hello("world")
                ),
            Execution.class
        );

        assertThat(execution).isNotNull();
        assertThat(execution.getId()).isNotNull();

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest
                    .POST(
                        "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                        new Hello("webhook")
                    ),
                Execution.class
            )
        );
        assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(e.getResponse().body()).isNull();
    }

    @Test
    @LoadFlows(value = { "flows/valids/webhook-inputs.yaml" })
    void webhookWithInputs() {
        record Hello(String hello) {
        }

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-inputs/webhookKey",
                    new Hello("world")
                ),
            Execution.class
        );

        assertThat(execution).isNotNull();
        assertThat(execution.getId()).isNotNull();
    }

    @Test
    void nullLabels() {
        MultipartBody requestBody = createExecutionInputsFlowBody();

        // null keys are forbidden
        MutableHttpRequest<MultipartBody> requestNullKey = HttpRequest
            .POST("/api/v1/main/executions/" + TESTS_FLOW_NS + "/inputs?labels=:value", requestBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(requestNullKey, Execution.class));

        // null values are forbidden
        MutableHttpRequest<MultipartBody> requestNullValue = HttpRequest
            .POST("/api/v1/main/executions/" + TESTS_FLOW_NS + "/inputs?labels=key:", requestBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(requestNullValue, Execution.class));
    }

    @Test
    void duplicatedLabels() {
        MultipartBody requestBody = createExecutionInputsFlowBody();

        // duplicated keys are forbidden
        MutableHttpRequest<MultipartBody> requestNullKey = HttpRequest
            .POST("/api/v1/main/executions/" + TESTS_FLOW_NS + "/inputs?labels=key:value1&labels=key:value2", requestBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(requestNullKey, Execution.class));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @LoadFlows(value = { "flows/valids/full.yaml" })
    void getExecutionFlowForExecution() {
        FlowForExecution result = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/flows/io.kestra.tests/full"),
            FlowForExecution.class
        );

        assertThat(result).isNotNull();
        assertThat(result.getTasks()).hasSize(5);
        assertThat((result.getTasks().getFirst() instanceof TaskForExecution)).isEqualTo(true);
    }

    @Test
    @LoadFlows(value = { "flows/valids/full.yaml" })
    void getExecutionFlowForExecutionWithOldUrl() {
        FlowForExecution result = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/flows/io.kestra.tests/full"),
            FlowForExecution.class
        );

        assertThat(result).isNotNull();
        assertThat(result.getTasks()).hasSize(5);
        assertThat((result.getTasks().getFirst() instanceof TaskForExecution)).isEqualTo(true);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    @LoadFlows(value = { "flows/valids/webhook.yaml" })
    void getExecutionFlowForExecutionById() {
        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + TESTS_WEBHOOK_KEY + "?name=john&age=12&age=13",
                    ImmutableMap.of("a", 1, "b", true)
                ),
            Execution.class
        );
        executionRepository.save(execution);

        FlowForExecution result = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/flow"),
            FlowForExecution.class
        );

        assertThat(result.getId()).isEqualTo(execution.getFlowId());
        assertThat(result.getTriggers()).hasSize(1);
        assertThat((result.getTriggers().getFirst() instanceof AbstractTriggerForExecution)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = { "flows/valids/minimal.yaml" })
    void getExecutionDistinctNamespaceExecutables() {
        List<String> result = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/namespaces"),
            Argument.of(List.class, String.class)
        );

        assertThat(result.size()).isGreaterThanOrEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = { "flows/valids/webhook.yaml", "flows/valids/minimal.yaml" })
    void getExecutionFlowFromNamespace() {
        List<FlowForExecution> result = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/namespaces/io.kestra.tests/flows"),
            Argument.of(List.class, FlowForExecution.class)
        );

        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDistinctFieldValues_flowId_returnsListOfStrings() {
        seedExecutionWithFlowId(MAIN_TENANT, "distinct-flow-test-a");
        seedExecutionWithFlowId(MAIN_TENANT, "distinct-flow-test-b");

        List<String> all = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/distinct-field-values?field=FLOW_ID&size=100"),
            Argument.of(List.class, String.class)
        );

        assertThat(all).contains("distinct-flow-test-a", "distinct-flow-test-b");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDistinctFieldValues_flowId_narrowsByContainsFilter() {
        seedExecutionWithFlowId(MAIN_TENANT, "distinct-flow-narrow-alpha");
        seedExecutionWithFlowId(MAIN_TENANT, "distinct-flow-narrow-beta");

        List<String> filtered = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/distinct-field-values?field=FLOW_ID&filters[flowId][CONTAINS]=narrow-alpha&size=100"),
            Argument.of(List.class, String.class)
        );

        assertThat(filtered).contains("distinct-flow-narrow-alpha");
        assertThat(filtered).doesNotContain("distinct-flow-narrow-beta");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDistinctFieldValues_namespace_returnsListOfStrings() {
        seedExecutionWithFlowIdAndNamespace(MAIN_TENANT, "distinct-ns-flow", "io.kestra.distinct.alpha");
        seedExecutionWithFlowIdAndNamespace(MAIN_TENANT, "distinct-ns-flow", "io.kestra.distinct.beta");

        List<String> namespaces = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/distinct-field-values?field=NAMESPACE&size=100"),
            Argument.of(List.class, String.class)
        );

        assertThat(namespaces).contains("io.kestra.distinct.alpha", "io.kestra.distinct.beta");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDistinctFieldValues_narrowsByCrossFieldFilter() {
        seedExecutionWithFlowIdAndNamespace(MAIN_TENANT, "cross-flow-in-target", "io.kestra.cross-test.target");
        seedExecutionWithFlowIdAndNamespace(MAIN_TENANT, "cross-flow-not-in-target", "io.kestra.cross-test.other");

        List<String> filtered = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/distinct-field-values?field=FLOW_ID&filters[namespace][EQUALS]=io.kestra.cross-test.target&size=100"),
            Argument.of(List.class, String.class)
        );

        assertThat(filtered).contains("cross-flow-in-target");
        assertThat(filtered).doesNotContain("cross-flow-not-in-target");
    }

    @Test
    void findDistinctFieldValues_unsupportedField_returnsBadRequest() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                GET("/api/v1/main/executions/distinct-field-values?field=USERNAME&size=100"),
                Argument.of(List.class, String.class)
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    private void seedExecutionWithFlowId(String tenant, String flowId) {
        seedExecutionWithFlowIdAndNamespace(tenant, flowId, "io.kestra.distinct-test");
    }

    private void seedExecutionWithFlowIdAndNamespace(String tenant, String flowId, String namespace) {
        executionRepository.save(
            Execution.builder()
                .id(io.kestra.core.utils.IdUtils.create())
                .namespace(namespace)
                .tenantId(tenant)
                .flowId(flowId)
                .flowRevision(1)
                .state(new io.kestra.core.models.flows.State().withState(io.kestra.core.models.flows.State.Type.SUCCESS))
                .build()
        );
    }

    @Test
    void badQueryFilters() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                GET(
                    "/api/v1/main/executions/search?filters[triggerId][EQUALS]=test"
                ), PagedResults.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(exception.getMessage()).isEqualTo(
            "Invalid query filters: Provided query filters are invalid: Field TRIGGER_ID is not supported for resource EXECUTION. Supported fields are QUERY, SCOPE, FLOW_ID, START_DATE, END_DATE, STATE, LABELS, TRIGGER_EXECUTION_ID, CHILD_FILTER, NAMESPACE, KIND, PARENT_ID"
        );

        exception = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                GET(
                    "/api/v1/main/executions/search?filters[startDate][EQUALS]=2024-06-03T00:00:00.000%2B02:00&filters[endDate][EQUALS]=2023-06-05T00:00:00.000%2B02:00"
                ), PagedResults.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(422);
        assertThat(exception.getMessage()).isEqualTo("Illegal argument: Start date must be before End Date");
    }

    @Test
    @LoadFlows(value = { "flows/valids/minimal.yaml" })
    void scheduleDate() {
        // given
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        String scheduleDate = URLEncoder.encode(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now), StandardCharsets.UTF_8);

        // when
        MutableHttpRequest<?> createRequest = HttpRequest
            .POST("/api/v1/main/executions/" + TESTS_FLOW_NS + "/minimal?scheduleDate=" + scheduleDate, null)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        Execution execution = client.toBlocking().retrieve(createRequest, Execution.class);

        // then
        assertThat(execution.getScheduleDate()).isEqualTo(now.toInstant());
    }

    @Test
    void shouldValidateInputsForCreateExecutionGivenSimpleInputs() {
        // given
        String namespace = "io.kestra.tests";
        String flowId = "inputs";

        MultipartBody requestBody = MultipartBody.builder()
            .addPart("string", "myString")
            .build();
        // when
        ExecutionController.ApiValidateExecutionInputsResponse response = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + namespace + "/" + flowId + "/validate", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            ExecutionController.ApiValidateExecutionInputsResponse.class
        );

        // then
        Assertions.assertNotNull(response);
        Assertions.assertEquals(flowId, response.id());
        Assertions.assertEquals(namespace, response.namespace());
        Assertions.assertFalse(response.inputs().isEmpty());
        Assertions.assertTrue(response.inputs().stream().allMatch(ExecutionController.ApiValidateExecutionInputsResponse.ApiInputAndValue::enabled));
    }

    @Test
    @LoadFlows(value = { "flows/valids/minimal.yaml" })
    void shouldHaveAnUrlWhenCreated() {
        // ExecutionController.ExecutionResponse cannot be deserialized because it didn't have any default constructor.
        // adding it would mean updating the Execution itself, which is too annoying, so for the test we just deserialize to a Map.
        Map<?, ?> executionResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + TESTS_FLOW_NS + "/minimal", null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Map.class
        );

        assertThat(executionResult).isNotNull();
        assertThat(executionResult.get("url")).isEqualTo("http://localhost:8081/ui/main/executions/io.kestra.tests/minimal/" + executionResult.get("id"));
    }

    @Test
    @LoadFlows(value = { "flows/valids/minimal.yaml" })
    void shouldRefuseSystemLabelsWhenCreatingAnExecution() {
        var error = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST("/api/v1/main/executions/io.kestra.tests/minimal?labels=system.label:system", null)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                Execution.class
            )
        );

        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldBlockExecutionAndThrowCheckErrorMessage() {
        String namespaceId = "io.othercompany";
        String flowId = "flowWithCheck";

        createFlowWithFailingCheck(namespaceId, flowId);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + namespaceId + "/" + flowId, null),
                Execution.class
            )
        );
        assertThat(e.getMessage()).contains("No VM provided");
    }

    void createFlowWithFailingCheck(String namespaceId, String flowId) {
        Flow create = Flow.builder()
            .id(flowId)
            .tenantId(MAIN_TENANT)
            .namespace(namespaceId)
            .checks(List.of(Check.builder().when("{{ [] | length > 0 }}").message("No VM provided").style(Check.Style.ERROR).behavior(Check.Behavior.BLOCK_EXECUTION).build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();

        client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/flows", create.sourceOrGenerateIfNull()).contentType(MediaType.APPLICATION_YAML_TYPE),
            Flow.class
        );
    }

    @Test
    void shouldNotLeakFilePreviewFromAnotherExecution() throws Exception {
        // Given - execution A exists in the repository
        String execAId = IdUtils.create();
        String execBId = IdUtils.create();
        String nsA = "io.kestra.test-ns-a";
        String nsB = "io.kestra.test-ns-b";

        Execution execA = Execution.builder()
            .id(execAId)
            .tenantId(MAIN_TENANT)
            .namespace(nsA)
            .flowId("flow-a")
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(execA);

        // Execution B does NOT exist in the repository, but a file is stored under its path
        URI execBFilePath = URI.create(
            "/" + nsB.replace(".", "/") + "/flow-b/executions/" + execBId + "/tasks/task1/tr1/output.txt"
        );
        URI execBFileUri = storageInterface.put(MAIN_TENANT, nsB, execBFilePath,
            new ByteArrayInputStream("sensitive-data".getBytes(StandardCharsets.UTF_8)));

        // When - request preview for execution A but supply a path belonging to execution B
        String encodedPath = URLEncoder.encode(execBFileUri.toString(), StandardCharsets.UTF_8);

        // Then - the request must NOT serve the file directly; it must redirect to execution B.
        // Since execution B is not in the repository the redirect target returns 404.
        // Before the fix the return value of validateFile() was discarded, causing the file
        // content ("sensitive-data") to be returned with HTTP 200.
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                GET("/api/v1/main/executions/" + execAId + "/file/preview?path=" + encodedPath),
                String.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

}
