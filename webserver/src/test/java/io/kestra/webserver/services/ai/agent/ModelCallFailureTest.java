package io.kestra.webserver.services.ai.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallFailureTest {
    private ServerSocket proxy;

    @BeforeEach
    void startProxy() throws IOException {
        proxy = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(this::refuseEveryRequest);
    }

    @AfterEach
    void stopProxy() throws IOException {
        proxy.close();
    }

    @Test
    void shouldNameTheProxyWhenTunnelToProviderIsRefused() throws Exception {
        Throwable failure = streamThroughProxy("https://provider.invalid/v1/chat");

        assertThat(ModelCallFailure.reason(failure)).contains("407 Proxy Authentication Required");
    }

    @Test
    void shouldReportHttpStatusWhenProviderCallIsRejected() throws Exception {
        Throwable failure = streamThroughProxy("http://provider.invalid/v1/chat");

        assertThat(ModelCallFailure.reason(failure)).startsWith("HTTP 407");
    }

    private Throwable streamThroughProxy(String url) throws Exception {
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        try (
            JdkHttpClient client = JdkHttpClient.builder()
                .httpClientBuilder(
                    java.net.http.HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", proxy.getLocalPort())))
                )
                .build()
        ) {
            client.execute(HttpRequest.builder().method(HttpMethod.POST).url(url).body("{}").build(), failure::complete);
            return failure.get(10, TimeUnit.SECONDS);
        }
    }

    private void refuseEveryRequest() {
        while (!proxy.isClosed()) {
            try (Socket socket = proxy.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                    // drain the request head
                }
                socket.getOutputStream().write(
                    "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"proxy\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII)
                );
                socket.getOutputStream().flush();
            } catch (IOException e) {
                return;
            }
        }
    }
}
