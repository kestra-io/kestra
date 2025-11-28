package io.kestra.plugin.core.http;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.http.client.HttpClientRequestException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.*;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.Rethrow.throwFunction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
class RequestTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{ \"hello\": \"world\" }");
            assertThat(output.getEncryptedBody()).isNull();
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void head() throws Exception {
        final String url = "https://sampletestfile.com/wp-content/uploads/2023/07/500KB-CSV.csv";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.ofValue(url))
            .method(Property.ofValue("HEAD"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Request.Output output = task.run(runContext);

        assertThat(output.getUri()).isEqualTo(URI.create(url));
        assertThat(output.getHeaders().get("content-length").getFirst()).isEqualTo("512789");
    }

    @Test
    void head404() throws Exception {
        final String url = "https://bdnb-data.s3.fr-par.scw.cloud/bnb_export_metropole_sql_dump.tar.gz";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.ofValue(url))
            .method(Property.ofValue("HEAD"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getResponse().getStatus().getCode()).isEqualTo(404);
    }

    @Test
    void redirect() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/redirect"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{ \"hello\": \"world\" }");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void params() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/params?foo=baz"))
                .params(Property.ofValue(Map.of(
                    "hello", "world",
                    "foo", "bar",
                    "bar", List.of("foo1", "foo2")
                )))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat((String) output.getBody()).contains("hello=world");
            assertThat((String) output.getBody()).contains("foo=baz");
            assertThat((String) output.getBody()).contains("foo=bar");
            assertThat((String) output.getBody()).contains("bar=foo1");
            assertThat((String) output.getBody()).contains("bar=foo2");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void noRedirect() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/redirect"))
                .options(HttpConfiguration.builder()
                    .followRedirects(Property.ofValue(false))
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getCode()).isEqualTo(301);
        }
    }

    @Test
    void allowFailed() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello417"))
                .options(HttpConfiguration.builder()
                    .allowFailed(Property.ofValue(true))
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{ \"hello\": \"world\" }");
            assertThat(output.getCode()).isEqualTo(417);
        }
    }

    @Test
    void failed() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello417"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getResponse().getStatus().getCode()).isEqualTo(417);
        }
    }

    @Test
    void failedPost() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/markdown"))
                .method(Property.ofValue("POST"))
                .body(Property.ofValue("# hello web!"))
                .contentType(Property.ofValue("text/markdown"))
                .options(HttpConfiguration.builder().defaultCharset(Property.ofValue(null)).build())
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getResponse().getStatus().getCode()).isEqualTo(417);
            assertThat(exception.getMessage()).contains("hello world");
            byte[] content = ((io.kestra.core.http.HttpRequest.ByteArrayRequestBody) exception.getRequest().getBody()).getContent();
            assertThat(new String(content)).contains("hello web");
        }
    }

    @Test
    void selfSigned() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST, "testssl");
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello"))
                .options(HttpConfiguration.builder()
                    .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.ofValue(Duration.ofSeconds(30))).build())
                    .ssl(SslOptions.builder().insecureTrustAllCertificates(Property.ofValue(true)).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{ \"hello\": \"world\" }");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void selfSignedFailed() {
        try (
            ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST, "testssl");
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello"))
                .options(HttpConfiguration.builder()
                    .allowFailed(Property.ofValue(true))
                    .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.ofValue(Duration.ofSeconds(30))).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            HttpClientRequestException exception = assertThrows(
                HttpClientRequestException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getMessage()).contains("unable to find valid certification path");
        }
    }

    @Test
    void json() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .method(Property.ofValue("POST"))
                .uri(Property.ofValue(server.getURL().toString() + "/post/json"))
                .body(Property.ofValue(JacksonMapper.ofJson().writeValueAsString(ImmutableMap.of("hello", "world"))))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{\"hello\":\"world\"}");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void form() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .method(Property.ofValue("POST"))
                .contentType(Property.ofValue(MediaType.APPLICATION_FORM_URLENCODED))
                .uri(Property.ofValue(server.getURL().toString() + "/post/url-encoded"))
                .headers(Property.ofValue(Map.of(
                    "test", "{{ inputs.test }}"
                )))
                .formData(Property.ofValue(ImmutableMap.of("hello", "world")))
                .build();


            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of(
                "test", "value"
            ));

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("world > value");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void multipart() throws Exception {
        File file = new File(Objects.requireNonNull(RequestTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );

        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .method(Property.ofValue("POST"))
                .contentType(Property.ofValue(MediaType.MULTIPART_FORM_DATA))
                .uri(Property.ofValue(server.getURL().toString() + "/post/multipart"))
                .formData(Property.ofValue(ImmutableMap.of("hello", "world", "file", fileStorage.toString())))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("world > " + IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8));
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void multipartCustomFilename() throws Exception {
        File file = new File(Objects.requireNonNull(RequestTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );

        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .method(Property.ofValue("POST"))
                .contentType(Property.ofValue(MediaType.MULTIPART_FORM_DATA))
                .uri(Property.ofValue(server.getURL().toString() + "/post/multipart"))
                .formData(Property.ofValue(ImmutableMap.of("hello", "world", "file", ImmutableMap.of("content", fileStorage.toString(), "name", "test.yml"))))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("world > " + IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8));
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void encrypted() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/hello"))
                .encryptBody(Property.ofValue(true))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            // when encrypted, this must not be the plaintext value
            assertThat(output.getBody()).isNull();
            assertThat(output.getEncryptedBody()).isNotEqualTo("{ \"hello\": \"world\" }");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void bytes() {
        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.ofValue("https://github.com/kestra-io.png"))
            .contentType(Property.ofValue("application/octet-stream"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getMessage()).contains("Illegal unicode code");
    }

    @Test
    void basicAuth() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();
        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/auth/basic"))
                .options(HttpConfiguration.builder()
                    .auth(BasicAuthConfiguration.builder().username(Property.ofValue("John"))
                        .password(Property.ofValue("p4ss")).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{\"hello\":\"John\"}");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void basicAuthOld() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();
        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/auth/basic"))
                .options(HttpConfiguration.builder()
                    .basicAuthUser("John")
                    .basicAuthPassword("p4ss")
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{\"hello\":\"John\"}");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void bearerAuth() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();
        ) {
            String id = IdUtils.create();

            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/auth/bearer"))
                .options(HttpConfiguration.builder()
                    .auth(BearerAuthConfiguration.builder().token(Property.ofValue(id)).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("{\"hello\":\"" + id + "\"}");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void specialContentType() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/content-type"))
                .method(Property.ofValue("POST"))
                .body(Property.ofValue("{}"))
                .contentType(Property.ofValue("application/vnd.campaignsexport.v1+json"))
                .options(HttpConfiguration.builder().logs(HttpConfiguration.LoggingType.values()).defaultCharset(null).build())
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("application/vnd.campaignsexport.v1+json");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Test
    void spaceInURI() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/uri with space"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody()).isEqualTo("Hello World");
            assertThat(output.getCode()).isEqualTo(200);
        }
    }

    @Controller
    static class MockController {

        private static final int LARGE_BODY_SIZE = 20 * 1024 * 1024; // 20MB > 19MB safeguard
        private static final String LARGE_BODY = "a".repeat(LARGE_BODY_SIZE);

        @Get("/hello")
        HttpResponse<String> hello() {
            return HttpResponse.ok("{ \"hello\": \"world\" }");
        }

        @Post("content-type")
        @Consumes("application/vnd.campaignsexport.v1+json")
        @Produces(MediaType.TEXT_PLAIN)
        public io.micronaut.http.HttpResponse<String> contentType(io.micronaut.http.HttpRequest<?> request, @Nullable @Body Map<String, String> body) {
            return io.micronaut.http.HttpResponse.ok(request.getContentType().orElseThrow().toString());
        }

        @Head("/hello")
        HttpResponse<String> head() {
            return HttpResponse.ok();
        }

        @Get("/hello417")
        HttpResponse<String> hello417() {
            return HttpResponse.status(HttpStatus.EXPECTATION_FAILED).body("{ \"hello\": \"world\" }");
        }

        @Get("/params")
        HttpResponse<String> params(HttpRequest<?> request) {
            return HttpResponse.ok(request.getUri().getRawQuery());
        }

        @Post("/markdown")
        @Consumes(MediaType.TEXT_MARKDOWN)
        @Produces(MediaType.TEXT_MARKDOWN)
        HttpResponse<String> postMarkdown() {
            return HttpResponse.status(HttpStatus.EXPECTATION_FAILED).body("# hello world");
        }

        @Get("/redirect")
        HttpResponse<String> redirect() {
            return HttpResponse.redirect(URI.create("/hello"));
        }

        @Get("/auth/basic")
        HttpResponse<String> basicAuth(HttpRequest<?> request) {
            return request.getHeaders()
                .getAuthorization()
                .filter(v -> v.startsWith("Basic "))
                .map(v -> {
                    String decode = new String(
                        Base64.getDecoder().decode(v.substring(6).getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8
                    );

                    return decode.split(":", 2);
                })
                .filter(a -> a[1].equals("p4ss"))
                .map(a -> HttpResponse.ok("{\"hello\":\"" + a[0] + "\"}"))
                .orElseThrow();
        }

        @Get("/auth/bearer")
        HttpResponse<String> bearerAuth(HttpRequest<?> request) {
            return request.getHeaders()
                .getAuthorization()
                .filter(v -> v.startsWith("Bearer "))
                .map(v -> v.substring(7))
                .map(a -> HttpResponse.ok("{\"hello\":\"" + a + "\"}"))
                .orElseThrow();
        }

        @Post(uri = "/post/json")
        HttpResponse<Map<String, String>> postBody(@Body Map<String, String> body) {
            return HttpResponse.ok(body);
        }

        @Post(uri = "/post/url-encoded", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        HttpResponse<String> postUrlEncoded(HttpRequest<?> request, String hello) {
            return HttpResponse.ok(hello + " > " + request.getHeaders().get("test"));
        }

        @Post(uri = "/post/multipart", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<String> multipart(HttpRequest<?> request, String hello, StreamingFileUpload file) throws IOException {
            File tempFile = File.createTempFile(file.getFilename(), "temp");

            Publisher<Boolean> uploadPublisher = file.transferTo(tempFile);

            return Mono.from(uploadPublisher)
                .map(throwFunction(success -> {
                    try (FileInputStream fileInputStream = new FileInputStream(tempFile)) {
                        return hello + " > " + IOUtils.toString(fileInputStream, StandardCharsets.UTF_8);
                    }
                }));
        }

        @Get("/uri%20with%20space")
        HttpResponse<String> uriWithSpace() {
            return HttpResponse.ok("Hello World");
        }

        @Get("/large")
        HttpResponse<String> large() {
            return HttpResponse.ok(LARGE_BODY);
        }
    }

    @Test
    void largeBodyFailsFast() {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();
        ) {
            Request task = Request.builder()
                .id(RequestTest.class.getSimpleName())
                .type(RequestTest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/large"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getMessage())
                .contains("Response body is too large to store in task outputs")
                .contains("Download");
        }
    }
}
