package io.kestra.plugin.core.http;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class DownloadTest {
    public static final String FILE = "http://speedtest.ftp.otenet.gr/files/test1Mb.db";
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
            .uri(FILE)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        Download.Output output = task.run(runContext);

        assertThat(
            IOUtils.toString(this.storageInterface.get(null, output.getUri()), StandardCharsets.UTF_8),
            is(IOUtils.toString(new URL(FILE).openStream(), StandardCharsets.UTF_8))
        );
        assertThat(output.getUri().toString(), endsWith(".db"));
    }

    @Test
    void noResponse() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(embeddedServer.getURI() + "/204")
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
            .failOnEmptyResponse(false)
            .type(DownloadTest.class.getName())
            .uri(embeddedServer.getURI() + "/204")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());
        Download.Output output = assertDoesNotThrow(() -> task.run(runContext));

        assertThat(output.getLength(), is(0L));
        assertThat(IOUtils.toString(this.storageInterface.get(null, output.getUri()), StandardCharsets.UTF_8), is(""));
    }

    @Test
    void error() throws Exception {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Download task = Download.builder()
            .id(DownloadTest.class.getSimpleName())
            .type(DownloadTest.class.getName())
            .uri(embeddedServer.getURI() + "/500")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> task.run(runContext)
        );

        assertThat(exception.getMessage(), is("Internal Server Error"));
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
    }
}
