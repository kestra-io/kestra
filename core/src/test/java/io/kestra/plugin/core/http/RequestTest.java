package io.kestra.plugin.core.http;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
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
                .uri(server.getURL().toString() + "/hello")
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
        final String url = "http://speedtest.ftp.otenet.gr/files/test100Mb.db";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(url)
            .method(HttpMethod.HEAD)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Request.Output output = task.run(runContext);

        assertThat(output.getUri(), is(URI.create(url)));
        assertThat(output.getHeaders().get("Content-Length").get(0), is("104857600"));
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
                .uri(server.getURL().toString() + "/hello417")
                .allowFailed(true)
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            assertThat(output.getBody(), is("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(417));
        }
    }

    @Test
    void selfSigned() throws Exception {
        final String url = "https://self-signed.badssl.com/";

        Request task = Request.builder()
            .id(RequestTest.class.getSimpleName())
            .type(RequestTest.class.getName())
            .uri(url)
            .allowFailed(true)
            .sslOptions(AbstractHttp.SslOptions.builder().insecureTrustAllCertificates(true).build())
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Request.Output output = task.run(runContext);

        assertThat(output.getUri(), is(URI.create(url)));
        assertThat((String) output.getBody(), containsString("self-signed.<br>badssl.com"));
        assertThat(output.getCode(), is(200));
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
                .method(HttpMethod.POST)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .uri(server.getURL().toString() + "/post/simple")
                .headers(ImmutableMap.of(
                    "test", "{{ inputs.test }}"
                ))
                .formData(ImmutableMap.of("hello", "world"))
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
                .method(HttpMethod.POST)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .uri(server.getURL().toString() + "/post/multipart")
                .formData(ImmutableMap.of("hello", "world", "file", fileStorage.toString()))
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
                .method(HttpMethod.POST)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .uri(server.getURL().toString() + "/post/multipart")
                .formData(ImmutableMap.of("hello", "world", "file", ImmutableMap.of("content", fileStorage.toString(), "name", "test.yml")))
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
                .uri(server.getURL().toString() + "/hello")
                .encryptBody(true)
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Request.Output output = task.run(runContext);

            // when encrypted, this must not be the plaintext value
            assertThat(output.getBody(), nullValue());
            assertThat(output.getEncryptedBody(), not("{ \"hello\": \"world\" }"));
            assertThat(output.getCode(), is(200));
        }
    }

    @Controller
    static class MockController {
        @Get("/hello")
        HttpResponse<String> hello() {
            return HttpResponse.ok("{ \"hello\": \"world\" }");
        }

        @Get("/hello417")
        HttpResponse<String> hello417() {
            return HttpResponse.status(HttpStatus.EXPECTATION_FAILED).body("{ \"hello\": \"world\" }");
        }

        @Post(uri = "/post/simple", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        HttpResponse<String> simple(HttpRequest<?> request, String hello) {
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
