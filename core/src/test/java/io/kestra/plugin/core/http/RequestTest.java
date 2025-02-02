package io.kestra.plugin.core.http;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.http.client.HttpClientRequestException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.*;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class RequestTest {
    @Inject
    private RunContextFactory runContextFactory;

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
                .uri(Property.of(server.getURL().toString() + "/hello"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{ \"hello\": \"world\" }"));
            assertThat(output.getEncryptedBody(), nullValue());
            assertThat(output.getCode(), is(200));
        }
    }

    @Test
    void head() throws Exception {
        final String url = "https://sampletestfile.com/wp-content/uploads/2023/07/500KB-CSV.csv";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.of(url))
            .method(Property.of("HEAD"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Request.Output output = task.run(runContext);

        assertThat(output.getUri(), is(URI.create(url)));
        assertThat(output.getHeaders().get("content-length").getFirst(), is("512789"));
    }


    @Test
    void head404() throws Exception {
        final String url = "https://bdnb-data.s3.fr-par.scw.cloud/bnb_export_metropole_sql_dump.tar.gz";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.of(url))
            .method(Property.of("HEAD"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getResponse().getStatus().getCode(), is(404));
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
                .uri(Property.of(server.getURL().toString() + "/redirect"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(200));
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
                .uri(Property.of(server.getURL().toString() + "/redirect"))
                .options(HttpConfiguration.builder()
                    .followRedirects(Property.of(false))
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getCode(), is(301));
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
                .uri(Property.of(server.getURL().toString() + "/hello417"))
                .options(HttpConfiguration.builder()
                    .allowFailed(Property.of(true))
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(417));
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
                .uri(Property.of(server.getURL().toString() + "/hello417"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            HttpClientResponseException exception = assertThrows(
                HttpClientResponseException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getResponse().getStatus().getCode(), is(417));
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
                .uri(Property.of(server.getURL().toString() + "/hello"))
                .options(HttpConfiguration.builder()
                    .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.of(Duration.ofSeconds(30))).build())
                    .ssl(SslOptions.builder().insecureTrustAllCertificates(Property.of(true)).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(200));
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
                .uri(Property.of(server.getURL().toString() + "/hello"))
                .options(HttpConfiguration.builder()
                    .allowFailed(Property.of(true))
                    .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.of(Duration.ofSeconds(30))).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            HttpClientRequestException exception = assertThrows(
                HttpClientRequestException.class,
                () -> task.run(runContext)
            );

            assertThat(exception.getMessage(), containsString("unable to find valid certification path"));
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
                .method(Property.of("POST"))
                .uri(Property.of(server.getURL().toString() + "/post/json"))
                .body(Property.of(JacksonMapper.ofJson().writeValueAsString(ImmutableMap.of("hello", "world"))))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{\"hello\":\"world\"}"));
            assertThat(output.getCode(), is(200));
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
                .method(Property.of("POST"))
                .contentType(Property.of(MediaType.APPLICATION_FORM_URLENCODED))
                .uri(Property.of(server.getURL().toString() + "/post/url-encoded"))
                .headers(Property.of(Map.of(
                    "test", "{{ inputs.test }}"
                )))
                .formData(Property.of(ImmutableMap.of("hello", "world")))
                .build();


            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of(
                "test", "value"
            ));

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("world > value"));
            assertThat(output.getCode(), is(200));
        }
    }

    @Test
    void multipart() throws Exception {
        File file = new File(Objects.requireNonNull(RequestTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            null,
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
                .method(Property.of("POST"))
                .contentType(Property.of(MediaType.MULTIPART_FORM_DATA))
                .uri(Property.of(server.getURL().toString() + "/post/multipart"))
                .formData(Property.of(ImmutableMap.of("hello", "world", "file", fileStorage.toString())))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("world > " + IOUtils.toString(new FileInputStream(file), Charsets.UTF_8)));
            assertThat(output.getCode(), is(200));
        }
    }

    @Test
    void multipartCustomFilename() throws Exception {
        File file = new File(Objects.requireNonNull(RequestTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            null,
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
                .method(Property.of("POST"))
                .contentType(Property.of(MediaType.MULTIPART_FORM_DATA))
                .uri(Property.of(server.getURL().toString() + "/post/multipart"))
                .formData(Property.of(ImmutableMap.of("hello", "world", "file", ImmutableMap.of("content", fileStorage.toString(), "name", "test.yml"))))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("world > " + IOUtils.toString(new FileInputStream(file), Charsets.UTF_8)));
            assertThat(output.getCode(), is(200));
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
                .uri(Property.of(server.getURL().toString() + "/hello"))
                .encryptBody(Property.of(true))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            // when encrypted, this must not be the plaintext value
            assertThat(output.getBody(), nullValue());
            assertThat(output.getEncryptedBody(), not("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(200));
        }
    }

    @Test
    void bytes() {
        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(Property.of("https://github.com/kestra-io.png"))
            .contentType(Property.of("application/octet-stream"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getMessage(), containsString("Illegal unicode code"));
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
                .uri(Property.of(server.getURL().toString() + "/auth/basic"))
                .options(HttpConfiguration.builder()
                    .auth(BasicAuthConfiguration.builder().username(Property.of("John"))
                        .password(Property.of("p4ss")).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{\"hello\":\"John\"}"));
            assertThat(output.getCode(), is(200));
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
                .uri(Property.of(server.getURL().toString() + "/auth/bearer"))
                .options(HttpConfiguration.builder()
                    .auth(BearerAuthConfiguration.builder().token(Property.of(id)).build())
                    .build()
                )
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, Map.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{\"hello\":\"" + id + "\"}"));
            assertThat(output.getCode(), is(200));
        }
    }

    @Controller
    static class MockController {
        @Get("/hello")
        HttpResponse<String> hello() {
            return HttpResponse.ok("{ \"hello\": \"world\" }");
        }

        @Head("/hello")
        HttpResponse<String> head() {
            return HttpResponse.ok();
        }

        @Get("/hello417")
        HttpResponse<String> hello417() {
            return HttpResponse.status(HttpStatus.EXPECTATION_FAILED).body("{ \"hello\": \"world\" }");
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
    }
}
