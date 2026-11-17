package io.kestra.core.http.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.HttpSseEvent;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.http.client.configurations.BearerAuthConfiguration;
import io.kestra.core.http.client.configurations.DigestAuthConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration.HttpVersion;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Timeout(60)
class HttpClientHttp2Test {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> workerTaskLogQueue;

    private static WireMockServer http2Server;
    private static WireMockServer http11Server;

    @BeforeAll
    static void startServers() {
        http2Server = new WireMockServer(WireMockConfiguration.options().dynamicPort().dynamicHttpsPort().http2PlainDisabled(false).http2TlsDisabled(false));
        http2Server.start();

        http11Server = new WireMockServer(WireMockConfiguration.options().dynamicPort().dynamicHttpsPort().http2PlainDisabled(true).http2TlsDisabled(true));
        http11Server.start();
    }

    @AfterAll
    static void stopServers() {
        http2Server.stop();
        http11Server.stop();
    }

    @BeforeEach
    void resetServers() {
        http2Server.resetAll();
        http11Server.resetAll();
    }

    @Test
    void shouldStayOnHttp11WhenHttpVersionIsNotSet() throws Exception {
        http2Server.stubFor(get("/hello").willReturn(ok("hello")));

        try (HttpClient client = HttpClient.builder().runContext(runContextFactory.of()).build()) {
            assertThat(client.request(HttpRequest.of(http(http2Server, "/hello")), String.class).getBody()).isEqualTo("hello");
        }

        assertThat(protocolOfLastRequest(http2Server)).isEqualTo("HTTP/1.1");
    }

    @Test
    void shouldUseHttp2WhenForcedOverCleartext() throws Exception {
        http2Server.stubFor(get("/hello").willReturn(ok("hello")));

        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            assertThat(client.request(HttpRequest.of(http(http2Server, "/hello")), String.class).getBody()).isEqualTo("hello");
        }

        assertThat(protocolOfLastRequest(http2Server)).startsWith("HTTP/2");
    }

    @Test
    void shouldUseHttp2WhenNegotiatedOverTls() throws Exception {
        http2Server.stubFor(get("/hello").willReturn(ok("hello")));

        try (HttpClient client = client(configuration(HttpVersion.NEGOTIATE))) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/hello")), String.class).getBody()).isEqualTo("hello");
        }

        assertThat(protocolOfLastRequest(http2Server)).startsWith("HTTP/2");
    }

    @Test
    void shouldFallBackToHttp11WhenNegotiatingWithAServerWithoutHttp2() throws Exception {
        http11Server.stubFor(get("/hello").willReturn(ok("hello")));

        try (HttpClient client = client(configuration(HttpVersion.NEGOTIATE))) {
            assertThat(client.request(HttpRequest.of(https(http11Server, "/hello")), String.class).getBody()).isEqualTo("hello");
        }

        assertThat(protocolOfLastRequest(http11Server)).isEqualTo("HTTP/1.1");
    }

    @Test
    void shouldSendBodyAndHeadersWhenUsingHttp2() throws Exception {
        http2Server.stubFor(post("/echo").willReturn(ok("received")));

        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            HttpResponse<String> response = client.request(
                HttpRequest.builder()
                    .uri(https(http2Server, "/echo"))
                    .method("POST")
                    .addHeader("X-Custom", "value")
                    .body(HttpRequest.StringRequestBody.builder().content("{\"hello\":\"world\"}").contentType("application/json").build())
                    .build(),
                String.class
            );

            assertThat(response.getBody()).isEqualTo("received");
        }

        http2Server.verify(
            postRequestedFor(urlEqualTo("/echo"))
                .withRequestBody(equalToJson("{\"hello\":\"world\"}"))
                .withHeader("X-Custom", equalTo("value"))
                .withHeader("User-Agent", equalTo("Kestra"))
                .withHeader("Accept-Encoding", equalTo("gzip, x-gzip, deflate"))
        );
    }

    @Test
    void shouldDecodeGzipResponseWhenUsingHttp2() throws Exception {
        String body = "compressible ".repeat(500);
        http2Server.stubFor(get("/gzip").willReturn(ok(body)));

        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/gzip")), String.class).getBody()).isEqualTo(body);
        }
    }

    @Test
    void shouldStreamResponseBodyWhenUsingHttp2() throws Exception {
        byte[] payload = new byte[2 * 1024 * 1024];
        new Random(42).nextBytes(payload);
        http2Server.stubFor(get("/big").willReturn(aResponse().withStatus(200).withBody(payload)));

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            client.request(HttpRequest.of(https(http2Server, "/big")), response ->
            {
                try {
                    IOUtils.copy(response.getBody(), received);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertThat(received.toByteArray()).isEqualTo(payload);
    }

    @Test
    void shouldConsumeServerSentEventsWhenUsingHttp2() throws Exception {
        http2Server.stubFor(
            get("/sse").willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody("id: 1\nevent: greeting\ndata: hello\n\nid: 2\ndata: world\n\n")
            )
        );

        List<HttpSseEvent<String>> events = new ArrayList<>();
        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            client.sseRequest(HttpRequest.of(https(http2Server, "/sse")), String.class, events::add);
        }

        assertThat(events).extracting(HttpSseEvent::data).containsExactly("hello", "world");
        assertThat(events).extracting(HttpSseEvent::name).containsExactly("greeting", null);
    }

    @Test
    void shouldFollowRedirectWhenUsingHttp2() throws Exception {
        http2Server.stubFor(get("/from").willReturn(temporaryRedirect("/to")));
        http2Server.stubFor(get("/to").willReturn(ok("arrived")));

        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/from")), String.class).getBody()).isEqualTo("arrived");
        }
    }

    @Test
    void shouldNotFollowRedirectWhenDisabledWithHttp2() throws Exception {
        http2Server.stubFor(get("/from").willReturn(temporaryRedirect("/to")));
        http2Server.stubFor(get("/to").willReturn(ok("arrived")));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2).followRedirects(Property.ofValue(false)).allowFailed(Property.ofValue(true)).build();
        try (HttpClient client = client(configuration)) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/from")), String.class).getStatus().getCode()).isEqualTo(302);
        }

        http2Server.verify(0, getRequestedFor(urlEqualTo("/to")));
    }

    @Test
    void shouldSendBasicAuthenticationWhenUsingHttp2() throws Exception {
        http2Server.stubFor(get("/secured").willReturn(ok("ok")));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2)
            .auth(BasicAuthConfiguration.builder().username(Property.ofValue("user")).password(Property.ofValue("pass")).build())
            .build();
        try (HttpClient client = client(configuration)) {
            client.request(HttpRequest.of(https(http2Server, "/secured")), String.class);
        }

        http2Server.verify(getRequestedFor(urlEqualTo("/secured")).withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz")));
    }

    @Test
    void shouldSendBearerAuthenticationWhenUsingHttp2() throws Exception {
        http2Server.stubFor(get("/secured").willReturn(ok("ok")));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2)
            .auth(BearerAuthConfiguration.builder().token(Property.ofValue("token")).build())
            .build();
        try (HttpClient client = client(configuration)) {
            client.request(HttpRequest.of(https(http2Server, "/secured")), String.class);
        }

        http2Server.verify(getRequestedFor(urlEqualTo("/secured")).withHeader("Authorization", equalTo("Bearer token")));
    }

    @Test
    void shouldAnswerDigestChallengeWhenUsingHttp2() throws Exception {
        http2Server.stubFor(
            get("/digest").atPriority(2).willReturn(
                aResponse().withStatus(401).withHeader("WWW-Authenticate", "Digest realm=\"kestra\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", qop=\"auth\", algorithm=MD5")
            )
        );
        http2Server.stubFor(get("/digest").atPriority(1).withHeader("Authorization", matching("Digest .*")).willReturn(ok("authenticated")));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2)
            .auth(DigestAuthConfiguration.builder().username(Property.ofValue("user")).password(Property.ofValue("pass")).build())
            .build();
        try (HttpClient client = client(configuration)) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/digest")), String.class).getBody()).isEqualTo("authenticated");
        }

        http2Server.verify(2, getRequestedFor(urlEqualTo("/digest")));
    }

    @Test
    void shouldRetryOnStatusCodeWhenUsingHttp2() throws Exception {
        http2Server.stubFor(get("/flaky").inScenario("flaky").whenScenarioStateIs("Started").willReturn(serviceUnavailable()).willSetStateTo("recovered"));
        http2Server.stubFor(get("/flaky").inScenario("flaky").whenScenarioStateIs("recovered").willReturn(ok("recovered")));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2)
            .retry(Exponential.builder().interval(Duration.ofMillis(10)).maxInterval(Duration.ofMillis(50)).maxAttempts(3).build())
            .build();
        try (HttpClient client = client(configuration)) {
            assertThat(client.request(HttpRequest.of(https(http2Server, "/flaky")), String.class).getBody()).isEqualTo("recovered");
        }
    }

    @Test
    void shouldThrowRequestExceptionWhenConnectionIsRefusedWithHttp2() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        try (HttpClient client = client(configuration(HttpVersion.HTTP_2))) {
            var exception = assertThrows(HttpClientRequestException.class, () -> client.request(HttpRequest.of(URI.create("http://localhost:" + closedPort + "/")), String.class));

            assertThat(exception.getCause()).isInstanceOf(ConnectException.class);
        }
    }

    @Test
    void shouldTimeoutWhenServerStaysSilentWithHttp2() throws Exception {
        http2Server.stubFor(get("/slow").willReturn(ok("late").withFixedDelay(3000)));

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2)
            .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.ofValue(Duration.ofMillis(500))).build())
            .build();
        try (HttpClient client = client(configuration)) {
            long start = System.nanoTime();

            assertThrows(Exception.class, () -> client.request(HttpRequest.of(https(http2Server, "/slow")), String.class));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void shouldLogHeadersAndWarnAboutBodiesWhenUsingHttp2() throws Exception {
        http2Server.stubFor(post("/log").willReturn(ok("response body").withHeader("X-Unit", "Response")));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.addListener(logs::add);
        RunContext runContext = runContextFactory.of(flow, execution);

        HttpConfiguration configuration = configurationBuilder(HttpVersion.HTTP_2).logs(HttpConfiguration.LoggingType.values()).build();
        try (HttpClient client = HttpClient.builder().runContext(runContext).configuration(configuration).build()) {
            HttpResponse<String> response = client.request(
                HttpRequest.builder()
                    .uri(https(http2Server, "/log"))
                    .method("POST")
                    .addHeader("X-Unit", "Request")
                    .body(HttpRequest.StringRequestBody.builder().content("request body").build())
                    .build(),
                String.class
            );

            assertThat(response.getBody()).isEqualTo("response body");

            // 3 entries for the request, 3 for the response and the warning about the bodies
            String messages = TestsUtils.awaitLogs(logs, 7).stream().map(LogEntry::getMessage).collect(Collectors.joining("\n"));
            assertThat(messages).contains("X-Unit: Request").containsIgnoringCase("X-Unit: Response");
            assertThat(messages).contains("The request and response bodies cannot be logged with the HTTP version HTTP_2");
        }
    }

    private static HttpConfiguration configuration(HttpVersion version) {
        return configurationBuilder(version).build();
    }

    private static HttpConfiguration.HttpConfigurationBuilder configurationBuilder(HttpVersion version) {
        return HttpConfiguration.builder()
            .httpVersion(Property.ofValue(version))
            .ssl(SslOptions.builder().insecureTrustAllCertificates(Property.ofValue(true)).build());
    }

    private HttpClient client(HttpConfiguration configuration) throws IllegalVariableEvaluationException {
        return HttpClient.builder().runContext(runContextFactory.of()).configuration(configuration).build();
    }

    private static URI https(WireMockServer server, String path) {
        return URI.create("https://localhost:" + server.httpsPort() + path);
    }

    private static URI http(WireMockServer server, String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static String protocolOfLastRequest(WireMockServer server) {
        return server.getAllServeEvents().getFirst().getRequest().getProtocol();
    }
}
