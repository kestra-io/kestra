package io.kestra.plugin.core.http;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DownloadTest {
    public static final String FILE = "https://sampletestfile.com/wp-content/uploads/2023/07/500KB-CSV.csv";
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ApplicationContext applicationContext;

    @Test
    void run() throws Exception {
        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(FILE))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(
            IOUtils.toString(this.storageInterface.get(null, null, output.getUri()), StandardCharsets.UTF_8),
            is(IOUtils.toString(new URI(FILE).toURL().openStream(), StandardCharsets.UTF_8))
        );
        assertThat(output.getUri().toString(), endsWith(".csv"));
    }

    @Test
    void noResponse() {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/204"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getMessage(), is("No response from server"));
    }

    @Test
    void allowNoResponse() throws IOException {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .failOnEmptyResponse(Property.of(false))
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/204"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());
        Download.Output output = assertDoesNotThrow(() -> task.run(runContext));

        assertThat(output.getLength(), is(0L));
        assertThat(IOUtils.toString(this.storageInterface.get(null, null, output.getUri()), StandardCharsets.UTF_8), is(""));
    }

    @Test
    void error() {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/500"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getMessage(), containsString("Failed http request with response code '500'"));
    }

    @Test
    void chunked() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/chunked"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(this.storageInterface.get(null, null, output.getUri()).readAllBytes().length, is(10000 * 12));
    }

    @Test
    void contentDisposition() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/content-disposition"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(output.getUri().toString(), endsWith("filename.jpg"));
    }

    @Test
    void contentDispositionWithPath() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/content-disposition"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(output.getUri().toString(), not(containsString("/secure-path/")));
        assertThat(output.getUri().toString(), endsWith("filename.jpg"));
    }

    @Test
    void failed() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        ) {
            Download task = Download.builder()
                .id(Download.class.getSimpleName())
                .type(Download.class.getName())
                .uri(Property.of(server.getURL().toString() + "/hello417"))
                .options(HttpConfiguration.builder().allowFailed(Property.of(true)).build())
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            Download.Output output = task.run(runContext);

            assertThat(output.getHeaders().get("content-type"), is(List.of("application/json")));
            assertThat(output.getCode(), is(417));
        }
    }

    @Test
    void contentDispositionWithDoubleDot() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(Property.of(embeddedServer.getURI() + "/content-disposition-double-dot"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(output.getUri().toString(), not(containsString("/secure-path/")));
        assertThat(output.getUri().toString(), endsWith("filename..jpg"));
    }

    @Controller()
    public static class SlackWebController {
        @Get("500")
        public HttpResponse<String> error() {
            return HttpResponse.serverError();
        }

        @Get("204")
        public HttpResponse<Void> noContent() {
            return HttpResponse.noContent();
        }


        @Get("chunked")
        public Flux<byte[]> chunked() {
            return Flux.create(sink -> {
                for (int i = 0; i < 10000; i++) {
                    sink.next("Hello World\n".getBytes());
                }
                sink.complete();
            }, FluxSink.OverflowStrategy.BUFFER);
        }

        @Get("content-disposition")
        public HttpResponse<byte[]> contentDisposition() {
            return HttpResponse.ok("Hello World".getBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"filename.jpg\"");
        }

        @Get("content-disposition-path")
        public HttpResponse<byte[]> contentDispositionWithPath() {
            return HttpResponse.ok("Hello World".getBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"/secure-path/filename.jpg\"");
        }

        @Get("content-disposition-double-dot")
        public HttpResponse<byte[]> contentDispositionWithDoubleDot() {
            return HttpResponse.ok("Hello World".getBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"/secure-path/filename..jpg\"");
        }
    }
}
