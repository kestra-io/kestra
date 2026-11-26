package io.kestra.webserver.utils;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Sends the JVM proxy credentials ({@code http(s).proxyUser} and {@code http(s).proxyPassword}) with every
 * request, which {@code java.net.http.HttpClient} never reads on its own.
 *
 * <p>The JDK only forwards a {@code Proxy-Authorization} header to a proxy, never to the target server. An
 * {@link Authenticator} is deliberately not used: once one is set, the JDK fails a 401 from the target server
 * instead of returning it. For an HTTPS target the JVM must also be started with
 * {@code -Djdk.http.auth.tunneling.disabledSchemes=}, since the JDK drops Basic credentials on a tunnel by default.
 */
public final class ProxyAuthorizingHttpClient extends HttpClient {
    private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    private static final List<String> SCHEMES = List.of("https", "http");

    private final HttpClient delegate;
    private final UnaryOperator<String> properties;

    private ProxyAuthorizingHttpClient(HttpClient delegate, UnaryOperator<String> properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    public static boolean isConfigured() {
        return SCHEMES.stream().anyMatch(scheme -> proxyAuthorization(System::getProperty, scheme).isPresent());
    }

    public static HttpClient.Builder builder(HttpClient.Builder delegate) {
        return builder(delegate, System::getProperty);
    }

    static HttpClient.Builder builder(HttpClient.Builder delegate, UnaryOperator<String> properties) {
        return new Builder(delegate, properties);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return delegate.send(authorize(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return delegate.sendAsync(authorize(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {
        return delegate.sendAsync(authorize(request), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return delegate.awaitTermination(duration);
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdownNow() {
        delegate.shutdownNow();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private HttpRequest authorize(HttpRequest request) {
        if (request.headers().firstValue(PROXY_AUTHORIZATION).isPresent()) {
            return request;
        }

        return proxyAuthorization(properties, request.uri().getScheme())
            .map(authorization -> HttpRequest.newBuilder(request, (name, value) -> true).header(PROXY_AUTHORIZATION, authorization).build())
            .orElse(request);
    }

    private static Optional<String> proxyAuthorization(UnaryOperator<String> properties, String scheme) {
        if (scheme == null || !SCHEMES.contains(scheme.toLowerCase())) {
            return Optional.empty();
        }

        String prefix = scheme.toLowerCase();
        String host = properties.apply(prefix + ".proxyHost");
        String user = properties.apply(prefix + ".proxyUser");
        if (host == null || host.isBlank() || user == null || user.isBlank()) {
            return Optional.empty();
        }

        String password = properties.apply(prefix + ".proxyPassword");
        String credentials = user + ":" + (password == null ? "" : password);
        return Optional.of("Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class Builder implements HttpClient.Builder {
        private final HttpClient.Builder delegate;
        private final UnaryOperator<String> properties;

        private Builder(HttpClient.Builder delegate, UnaryOperator<String> properties) {
            this.delegate = delegate;
            this.properties = properties;
        }

        @Override
        public HttpClient build() {
            return new ProxyAuthorizingHttpClient(delegate.build(), properties);
        }

        @Override
        public Builder cookieHandler(CookieHandler cookieHandler) {
            delegate.cookieHandler(cookieHandler);
            return this;
        }

        @Override
        public Builder connectTimeout(Duration duration) {
            delegate.connectTimeout(duration);
            return this;
        }

        @Override
        public Builder sslContext(SSLContext sslContext) {
            delegate.sslContext(sslContext);
            return this;
        }

        @Override
        public Builder sslParameters(SSLParameters sslParameters) {
            delegate.sslParameters(sslParameters);
            return this;
        }

        @Override
        public Builder executor(Executor executor) {
            delegate.executor(executor);
            return this;
        }

        @Override
        public Builder followRedirects(Redirect policy) {
            delegate.followRedirects(policy);
            return this;
        }

        @Override
        public Builder version(Version version) {
            delegate.version(version);
            return this;
        }

        @Override
        public Builder priority(int priority) {
            delegate.priority(priority);
            return this;
        }

        @Override
        public Builder proxy(ProxySelector proxySelector) {
            delegate.proxy(proxySelector);
            return this;
        }

        @Override
        public Builder authenticator(Authenticator authenticator) {
            delegate.authenticator(authenticator);
            return this;
        }
    }
}
