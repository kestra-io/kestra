package io.kestra.webserver.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyAuthorizingHttpClientTest {
    private static final String PROXY_AUTHORIZATION = "Proxy-Authorization: Basic " + Base64.getEncoder().encodeToString("bob:secret".getBytes(StandardCharsets.UTF_8));
    private static final Map<String, String> PROXY_PROPERTIES = Map.of(
        "http.proxyHost", "proxy.internal",
        "http.proxyUser", "bob",
        "http.proxyPassword", "secret"
    );

    private ServerSocket server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    @Test
    void shouldSendProxyCredentialsWhenRequestGoesThroughProxy() throws Exception {
        answer("407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"proxy\"", "");
        HttpClient.Builder builder = HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", server.getLocalPort())));

        try (HttpClient client = ProxyAuthorizingHttpClient.builder(builder, PROXY_PROPERTIES::get).build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://provider.invalid/")).build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(requests).singleElement().asString().contains(PROXY_AUTHORIZATION);
        }
    }

    @Test
    void shouldReturnProviderErrorWhenProviderRejectsApiKey() throws Exception {
        answer("401 Unauthorized", "invalid x-api-key");
        HttpClient.Builder builder = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY);

        try (HttpClient client = ProxyAuthorizingHttpClient.builder(builder, PROXY_PROPERTIES::get).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/")).header("x-api-key", "wrong").build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).isEqualTo("invalid x-api-key");
            assertThat(requests).singleElement().asString().doesNotContainIgnoringCase("Proxy-Authorization");
        }
    }

    private void answer(String rejection, String body) {
        Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    StringBuilder request = new StringBuilder();
                    for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                        request.append(line).append('\n');
                    }
                    requests.add(request.toString());

                    boolean authorized = request.toString().contains(PROXY_AUTHORIZATION);
                    String response = "HTTP/1.1 " + (authorized ? "200 OK" : rejection) + "\r\nContent-Length: " + (authorized ? 0 : body.length()) + "\r\nConnection: close\r\n\r\n" + (authorized ? "" : body);
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    return;
                }
            }
        });
    }
}
