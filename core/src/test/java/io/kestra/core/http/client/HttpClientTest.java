package io.kestra.core.http.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.net.HttpHeaders;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.RetryingTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ArrayUtils.toPrimitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Testcontainers
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class HttpClientTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private TestRunContextFactory runContextFactory;

    private URI embeddedServerUri;

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> proxy = new GenericContainer<>("kalaksi/tinyproxy")
        .withExposedPorts(8888)
        .withEnv(Map.of("AUTH_USER", "pr0xy", "AUTH_PASSWORD", "p4ss"));

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @BeforeEach
    void setUp() {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();
        embeddedServerUri = embeddedServer.getURI();
    }

    HttpClient client() throws IllegalVariableEvaluationException {
        return client(null);
    }

    HttpClient client(@Nullable Consumer<HttpClient.HttpClientBuilder> consumer) throws IllegalVariableEvaluationException {
        HttpClient.HttpClientBuilder builder = HttpClient
            .builder()
            .runContext(runContextFactory.of());

        if (consumer != null) {
            consumer.accept(builder);
        }

        return builder.build();
    }

    @RetryingTest(5) // Flaky on CI but never locally even with 100 repetitions
    void getText() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        RunContext runContext = runContextFactory.of(flow, execution);

        HttpClient.HttpClientBuilder configuration = HttpClient
            .builder()
            .runContext(runContext)
            .configuration(HttpConfiguration.builder().logs(HttpConfiguration.LoggingType.values()).build());

        try (HttpClient client = configuration.build()) {
            HttpResponse<String> response = client.request(
                HttpRequest.builder()
                    .uri(URI.create(embeddedServerUri + "/http/text"))
                    .addHeader("X-Unit", "Test")
                    .build(),
                String.class
            );


            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("pong");

            List<LogEntry> logEntries = TestsUtils.awaitLogs(logs, 6);

            assertThat(logEntries.stream().filter(logEntry -> logEntry.getMessage().startsWith("request")).count()).isEqualTo(3L);
            assertThat(logEntries.stream().filter(logEntry -> logEntry.getMessage().contains("X-Unit: Test")).count()).isEqualTo(1L);
            assertThat(logEntries.stream().filter(logEntry -> logEntry.getMessage().startsWith("response")).count()).isEqualTo(3L);
        }
    }

    @Test
    void getByte() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<Byte[]> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/text")),
                Byte[].class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(toPrimitive(response.getBody())).isEqualTo("pong".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void getEmpty() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<String> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/empty")),
                String.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(204);
            assertThat(response.getBody()).isNull();
        }
    }

    @Test
    void getJsonMap() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<Map<String, String>> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json"))
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody().get("ping")).isEqualTo("pong");
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void getJsonList() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<List<Integer>> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json?array=true"))
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void getJsonAsText() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<String> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json")),
                String.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("{\"ping\":\"pong\"}");
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    private static final String UUID = IdUtils.create();

    static Stream<Arguments> postJsonSource() throws JsonProcessingException {
        return Stream.of(
            Arguments.of(HttpRequest.JsonRequestBody.builder().content(Map.of("ping", UUID)).build()),
            Arguments.of(HttpRequest.InputStreamRequestBody.builder()
                .content(new ByteArrayInputStream(JacksonMapper.ofJson().writeValueAsBytes(Map.of("ping", UUID))))
                .contentType(MediaType.APPLICATION_JSON)
                .build()
            ),
            Arguments.of(HttpRequest.ByteArrayRequestBody.builder()
                .content(JacksonMapper.ofJson().writeValueAsBytes(Map.of("ping", UUID)))
                .contentType(MediaType.APPLICATION_JSON)
                .build()
            )

        );
    }

    @ParameterizedTest
    @MethodSource("postJsonSource")
    void postJson(HttpRequest.RequestBody requestBody) throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<Map<String, String>> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json-post"), "POST", requestBody)
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody().get("ping")).isEqualTo(UUID);
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void postCustomObject() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        CustomObject test = CustomObject.builder()
            .id(IdUtils.create())
            .name("test")
            .build();

        try (HttpClient client = client()) {
            HttpResponse<CustomObject> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json-post"), "POST", HttpRequest.JsonRequestBody.builder().content(test).build()),
                CustomObject.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody().id).isEqualTo(test.id);
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void postCustomObject_WithUnknownResponseField() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        CustomObject test = CustomObject.builder()
            .id(IdUtils.create())
            .name("test")
            .build();

        Map<String, String> withAdditionalField = JacksonMapper.ofJson().convertValue(test, new TypeReference<>() {
        });

        withAdditionalField.put("foo", "bar");

        try (HttpClient client = client()) {
            HttpResponse<CustomObject> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/json-post"), "POST", HttpRequest.JsonRequestBody.builder().content(withAdditionalField).build()),
                CustomObject.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody().id).isEqualTo(test.id);
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void postMultipart() throws IOException, URISyntaxException, IllegalVariableEvaluationException, HttpClientException {
        Map<String, Object> multipart = Map.of(
            "ping", "pong",
            "int", 1,
             "file", new File(Objects.requireNonNull(this.getClass().getClassLoader().getResource("logback.xml")).toURI()),
            "inputStream", new ByteArrayInputStream(IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("logback.xml")),
                    StandardCharsets.UTF_8
                )
                .getBytes(StandardCharsets.UTF_8)
            )
        );

        try (HttpClient client = client()) {
            HttpResponse<Map<String, Object>> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/multipart"), "POST", HttpRequest.MultipartRequestBody.builder().content(multipart).build())
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody().get("ping")).isEqualTo("pong");
            assertThat(response.getBody().get("int")).isEqualTo("1");
            assertThat((String) response.getBody().get("file")).contains("logback");
            // @FIXME: Request seems to be correct, but not returned by micronaut
            // assertThat((String) response.getBody().get("inputStream"), containsString("logback"));
            assertThat(response.getHeaders().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void errorUnreachable() throws IOException, IllegalVariableEvaluationException {
        try (HttpClient client = client()) {
            URI uri = URI.create("http://localhost:1234");

            HttpClientRequestException e = assertThrows(HttpClientRequestException.class, () -> {
                client.request(HttpRequest.of(uri));
            });

            assertThat(e.getRequest().getUri()).isEqualTo(uri);
            assertThat(e.getMessage()).contains("Connection refused");
        }
    }

    @Test
    void response305() throws IOException, IllegalVariableEvaluationException, HttpClientException {
        try (HttpClient client = client()) {
            HttpResponse<Map<String, String>> response = client.request(HttpRequest.of(URI.create(embeddedServerUri + "/http/error?status=305")));

            assertThat(response.getStatus().getCode()).isEqualTo(305);
        }
    }

    @Test
    void error400() throws IOException, IllegalVariableEvaluationException {
        try (HttpClient client = client()) {
            URI uri = URI.create(embeddedServerUri + "/http/error");

            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
                client.request(HttpRequest.of(uri));
            });

            assertThat(Objects.requireNonNull(e.getResponse()).getStatus().getCode()).isEqualTo(400);
            assertThat(e.getMessage()).contains("Required QueryValue [status]");
            assertThat(new String((byte[]) e.getResponse().getBody())).contains("Required QueryValue [status]");
        }
    }

    @Test
    void error404() throws IOException, IllegalVariableEvaluationException {
        try (HttpClient client = client()) {
            URI uri = URI.create(embeddedServerUri + "/http/error?status=404");

            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
                client.request(HttpRequest.of(uri));
            });

            assertThat(Objects.requireNonNull(e.getResponse()).getStatus().getCode()).isEqualTo(404);
        }
    }

    @Test
    void noError404() throws IOException, IllegalVariableEvaluationException, HttpClientException {
        try (HttpClient client = client(b -> b.configuration(HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build()))) {
            HttpResponse<Map<String, String>> response = client.request(HttpRequest.of(URI.create(embeddedServerUri + "/http/error?status=404")));

            assertThat(response.getStatus().getCode()).isEqualTo(404);
        }
    }

    @Test
    void noErrorPost404() throws IOException, IllegalVariableEvaluationException, HttpClientException {
        try (HttpClient client = client(b -> b.configuration(HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build()))) {
            URI uri = URI.create(embeddedServerUri + "/http/post-error");

            HttpResponse<Map<String, String>> response = client.request(HttpRequest.builder().uri(uri).method("POST").body(HttpRequest.StringRequestBody.builder().content("OK").build()).build());

            assertThat(response.getStatus().getCode()).isEqualTo(404);
        }
    }

    @Test
    void specialContentType() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client()) {
            HttpResponse<String> response = client.request(
                HttpRequest.of(URI.create(embeddedServerUri + "/http/content-type"), Map.of(
                    "Content-Type", List.of("application/vnd.campaignsexport.v1+json")
                )),
                String.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("application/vnd.campaignsexport.v1+json");
        }
    }

    @Test
    void getProxy() throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = client(b -> b
            .configuration(HttpConfiguration.builder()
                .proxy(ProxyConfiguration.builder()
                    .type(Property.ofValue(Proxy.Type.HTTP))
                    .address(Property.ofValue(proxy.getHost()))
                    .username(Property.ofValue("pr0xy"))
                    .password(Property.ofValue("p4ss"))
                    .port(Property.ofValue(proxy.getFirstMappedPort()))
                    .build())
                .build()))
        ) {
            HttpResponse<String> response = client.request(
                HttpRequest.of(URI.create("https://www.google.com")),
                String.class
            );

            assertThat(response.getStatus().getCode()).isEqualTo(200);
            assertThat(response.getBody()).contains("<html");
        }
    }

    @Test
    void shouldReturnResponseForAllowedResponseCode() throws IOException, IllegalVariableEvaluationException, HttpClientException {
        try (HttpClient client = client(b -> b.configuration(HttpConfiguration.builder().allowedResponseCodes(Property.of(List.of(404))).build()))) {
            HttpResponse<Map<String, String>> response = client.request(HttpRequest.of(URI.create(embeddedServerUri + "/http/error?status=404")));

            assertThat(response.getStatus().getCode()).isEqualTo(404);
        }
    }

    @Test
    void shouldThrowExceptionForNotAllowedResponseCode() throws IOException, IllegalVariableEvaluationException {
        try (HttpClient client = client(b -> b.configuration(HttpConfiguration.builder().allowedResponseCodes(Property.of(List.of(404))).build()))) {
            URI uri = URI.create(embeddedServerUri + "/http/error?status=405");

            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
                client.request(HttpRequest.of(uri));
            });

            assertThat(Objects.requireNonNull(e.getResponse()).getStatus().getCode()).isEqualTo(405);
        }
    }

    @Controller("/http/")
    public static class ClientTestController {
        @SuppressWarnings("JsonStandardCompliance")
        @Get("text")
        @Produces(MediaType.TEXT_PLAIN)
        public io.micronaut.http.HttpResponse<String> text() {
            return io.micronaut.http.HttpResponse.ok("pong");
        }

        @Get("content-type")
        @Produces(MediaType.TEXT_PLAIN)
        public io.micronaut.http.HttpResponse<String> contentType(io.micronaut.http.HttpRequest<?> request) {
            return io.micronaut.http.HttpResponse.ok(request.getContentType().orElseThrow().toString());
        }

        @Get("json")
        public io.micronaut.http.HttpResponse<Object> json(@QueryValue(defaultValue = "false") Boolean array) {
            return io.micronaut.http.HttpResponse.ok(array ? List.of(1, 2, 3) : Map.of("ping", "pong"));
        }

        @Post("json-post")
        public io.micronaut.http.HttpResponse<Object> jsonPost(@Body Map<String, Object> data) {
            return io.micronaut.http.HttpResponse.ok(data);
        }

        @Get("empty")
        public io.micronaut.http.HttpResponse<Object> empty() {
            return io.micronaut.http.HttpResponse.noContent();
        }

        @Get("no-content")
        public io.micronaut.http.HttpResponse<Void> noContent() {
            return io.micronaut.http.HttpResponse.noContent();
        }

        @Get("error")
        public io.micronaut.http.HttpResponse<Object> errors(@QueryValue int status) {
            return io.micronaut.http.HttpResponse
                .status(HttpStatus.valueOf(status))
                .body(Map.of("status", status));
        }

        @Post("error")
        public io.micronaut.http.HttpResponse<Object> postErrors(@QueryValue int status, @Body String body) {
            return io.micronaut.http.HttpResponse
                .status(HttpStatus.valueOf(status))
                .body(Map.of("status", status));
        }

        @ExecuteOn(TaskExecutors.IO)
        @Post(uri = "multipart", consumes = MediaType.MULTIPART_FORM_DATA)
        public io.micronaut.http.HttpResponse<Object> multipartPost(@Body MultipartBody body) {
            Map<String, String> result = Flux.from(body)
                .<AbstractMap.SimpleEntry<String, String>>handle((input, sink) -> {
                    if (input instanceof CompletedFileUpload fileUpload) {
                        try {
                            try (var inputStream = fileUpload.getInputStream()) {
                                sink.next(new AbstractMap.SimpleEntry<>(
                                    fileUpload.getName(),
                                    IOUtils.toString(inputStream, StandardCharsets.UTF_8)
                                ));
                            }
                        } catch (IOException e) {
                            fileUpload.discard();
                            sink.error(e);
                        }
                    } else {
                        try {
                            sink.next(new AbstractMap.SimpleEntry<>(input.getName(), new String(input.getBytes())));
                        } catch (IOException e) {
                            sink.error(e);
                        }
                    }
                })
                .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
                .block();

            return io.micronaut.http.HttpResponse.ok(result);
        }
    }

    @Builder
    @Value
    public static class CustomObject {
        String id;
        String name;
    }
}
