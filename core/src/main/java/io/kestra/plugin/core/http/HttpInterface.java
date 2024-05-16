package io.kestra.plugin.core.http;

import io.kestra.core.models.annotations.PluginProperty;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.logging.LogLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public interface HttpInterface {
    @Schema(
            title = "The fully-qualified URI that points to the HTTP destination"
    )
    @PluginProperty(dynamic = true)
    String getUri();

    @Schema(
            title = "The HTTP method to use"
    )
    @PluginProperty
    HttpMethod getMethod();

    @Schema(
            title = "The full body as a string"
    )
    @PluginProperty(dynamic = true)
    String getBody();

    @Schema(
            title = "The form data to be send"
    )
    @PluginProperty(dynamic = true)
    Map<String, Object> getFormData();

    @Schema(
            title = "The request content type"
    )
    @PluginProperty(dynamic = true)
    String getContentType();

    @Schema(
            title = "The headers to pass to the request"
    )
    @PluginProperty(dynamic = true)
    Map<CharSequence, CharSequence> getHeaders();

    @Schema(
            title = "The HTTP request options"
    )
    RequestOptions getOptions();

    @Schema(
            title = "The SSL request options"
    )
    SslOptions getSslOptions();

    @Getter
    @Builder
    class RequestOptions {
        @Schema(title = "The connect timeout.")
        @PluginProperty
        private final Duration connectTimeout;

        @Schema(title = "The default read timeout.")
        @Builder.Default
        @PluginProperty
        private final Duration readTimeout = Duration.ofSeconds(HttpClientConfiguration.DEFAULT_READ_TIMEOUT_SECONDS);

        @Schema(title = "The default amount of time to allow the read connection to remain idle.")
        @Builder.Default
        @PluginProperty
        private final Duration readIdleTimeout = Duration.of(HttpClientConfiguration.DEFAULT_READ_IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

        @Schema(title = "The idle timeout for connection in the client connection pool.")
        @Builder.Default
        @PluginProperty
        private final Duration connectionPoolIdleTimeout = Duration.ofSeconds(HttpClientConfiguration.DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS);

        @Schema(title = "The maximum content length of the response")
        @Builder.Default
        @PluginProperty
        private final Integer maxContentLength = HttpClientConfiguration.DEFAULT_MAX_CONTENT_LENGTH;

        @Schema(title = "The proxy type.")
        @Builder.Default
        @PluginProperty
        private final Proxy.Type proxyType = Proxy.Type.DIRECT;

        @Schema(title = "The proxy address.")
        @PluginProperty(dynamic = true)
        private final String proxyAddress;

        @Schema(title = "The proxy port.")
        @PluginProperty
        private final Integer proxyPort;

        @Schema(title = "The proxy username.")
        @PluginProperty(dynamic = true)
        private final String proxyUsername;

        @Schema(title = "The proxy password.")
        @PluginProperty(dynamic = true)
        private final String proxyPassword;

        @Schema(title = "The default charset.")
        @Builder.Default
        @PluginProperty
        private final Charset defaultCharset = StandardCharsets.UTF_8;

        @Schema(title = "Whether redirects should be followed.")
        @Builder.Default
        @PluginProperty
        private final Boolean followRedirects = HttpClientConfiguration.DEFAULT_FOLLOW_REDIRECTS;

        @Schema(title = "The log level.")
        @PluginProperty
        private final LogLevel logLevel;

        @Schema(title = "The HTTP basic authentication username.")
        @PluginProperty(dynamic = true)
        private final String basicAuthUser;

        @Schema(title = "The HTTP basic authentication password.")
        @PluginProperty(dynamic = true)
        private final String basicAuthPassword;
    }

    @Getter
    @Builder
    class SslOptions {
        @Schema(
                title = "Whether the client should disable checking of the remote SSL certificate.",
                description = "Only applies if no trust store is configured. Note: This makes the SSL connection insecure, and should only be used for testing. If you are using a self-signed certificate, set up a trust store instead."
        )
        @PluginProperty
        private final Boolean insecureTrustAllCertificates;
    }
}
