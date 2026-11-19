package io.kestra.core.plugins;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.plugins.configuration.PluginsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class MavenPluginDownloaderTest {

    @TempDir
    Path remote;

    @TempDir
    Path local;

    @Test
    void shouldAnswerTheProxyChallengeWithTheJvmProxyCredentials() throws IOException {
        publishRelease("1.0.0", "proxied build");
        String expectedCredentials = "Basic " + Base64.getEncoder().encodeToString("kestra:s3cret".getBytes(StandardCharsets.UTF_8));
        AtomicInteger challenges = new AtomicInteger();
        HttpServer proxy = start(exchange -> {
            if (!expectedCredentials.equals(exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
                challenges.incrementAndGet();
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"proxy\"");
                exchange.sendResponseHeaders(407, -1);
                exchange.close();
                return;
            }
            serveFromRemote(exchange, "/maven/");
        });

        Properties systemProperties = new Properties();
        systemProperties.setProperty("http.proxyHost", "127.0.0.1");
        systemProperties.setProperty("http.proxyPort", String.valueOf(proxy.getAddress().getPort()));
        systemProperties.setProperty("http.proxyUser", "kestra");
        systemProperties.setProperty("http.proxyPassword", "s3cret");

        try (MavenPluginDownloader downloader = new MavenPluginDownloader(
            List.of(new MavenPluginRepositoryConfig("behind-proxy", "http://plugins.example.test/maven/", null)),
            new PluginsConfiguration(local.toString()),
            systemProperties
        )) {
            assertThat(Files.readString(Path.of(downloader.resolve("io.kestra.test:plugin-test:1.0.0").uri()))).isEqualTo("proxied build");
            assertThat(challenges).hasPositiveValue();
        } finally {
            proxy.stop(0);
        }
    }

    @Test
    void shouldReachARepositoryDirectlyWhenItsHostIsANonProxyHost() throws IOException {
        publishRelease("1.0.0", "direct build");
        HttpServer repository = start(exchange -> serveFromRemote(exchange, "/"));

        Properties systemProperties = new Properties();
        systemProperties.setProperty("http.proxyHost", "127.0.0.1");
        systemProperties.setProperty("http.proxyPort", "1");
        systemProperties.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");

        try (MavenPluginDownloader downloader = new MavenPluginDownloader(
            List.of(new MavenPluginRepositoryConfig("internal", "http://127.0.0.1:%d/".formatted(repository.getAddress().getPort()), null)),
            new PluginsConfiguration(local.toString()),
            systemProperties
        )) {
            assertThat(Files.readString(Path.of(downloader.resolve("io.kestra.test:plugin-test:1.0.0").uri()))).isEqualTo("direct build");
        } finally {
            repository.stop(0);
        }
    }

    private static HttpServer start(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private void serveFromRemote(HttpExchange exchange, String basePath) throws IOException {
        Path file = remote.resolve(exchange.getRequestURI().getPath().substring(basePath.length()));
        if (!Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] content = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(content);
        }
    }

    private void publishRelease(String version, String content) throws IOException {
        Path versionDirectory = Files.createDirectories(remote.resolve("io/kestra/test/plugin-test/" + version));
        Files.writeString(versionDirectory.resolve("plugin-test-" + version + ".jar"), content);
    }
}
