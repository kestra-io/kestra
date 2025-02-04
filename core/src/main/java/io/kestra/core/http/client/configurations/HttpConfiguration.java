package io.kestra.core.http.client.configurations;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
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

@Builder(toBuilder = true)
@Getter
public class HttpConfiguration {
    @Schema(title = "The timeout configuration.")
    @PluginProperty
    private TimeoutConfiguration timeout;

    @Schema(title = "The proxy configuration.")
    @PluginProperty
    private ProxyConfiguration proxy;

    @Schema(title = "The authentification to use.")
    private AbstractAuthConfiguration auth;

    @Schema(title = "The SSL request options")
    private SslOptions ssl;

    @Schema(title = "Whether redirects should be followed automatically.")
    @Builder.Default
    private Property<Boolean> followRedirects = Property.of(true);

    @Schema(title = "If true, allow a failed response code (response code >= 400)")
    @Builder.Default
    private Property<Boolean> allowFailed = Property.of(false);

    @Schema(title = "The default charset for the request.")
    @Builder.Default
    private final Property<Charset> defaultCharset = Property.of(StandardCharsets.UTF_8);

    @Schema(title = "The enabled log.")
    @PluginProperty
    private LoggingType[] logs;

    public enum LoggingType {
        REQUEST_HEADERS,
        REQUEST_BODY,
        RESPONSE_HEADERS,
        RESPONSE_BODY
    }

    // Deprecated properties

    /**
     * @deprecated
     */
    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    @Deprecated
    private final Property<Duration> connectTimeout;

    /**
     * @deprecated
     */
    @Deprecated
    public void setConnectTimeout(Property<Duration> connectTimeout) {
        if (this.timeout == null) {
            this.timeout = TimeoutConfiguration.builder()
                .build();
        }

        this.timeout = this.timeout.toBuilder()
            .connectTimeout(connectTimeout)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The maximum time allowed for reading data from the server before failing.")
    @Builder.Default
    @Deprecated
    private final Property<Duration> readTimeout = Property.of(Duration.ofSeconds(HttpClientConfiguration.DEFAULT_READ_TIMEOUT_SECONDS));

    /**
     * @deprecated
     */
    @Deprecated
    public void setReadTimeout(Property<Duration> readTimeout) {
        if (this.timeout == null) {
            this.timeout = TimeoutConfiguration.builder()
                .build();
        }

        this.timeout = this.timeout.toBuilder()
            .readIdleTimeout(readTimeout)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The type of proxy to use.")
    @Builder.Default
    @Deprecated
    private final Property<Proxy.Type> proxyType = Property.of(Proxy.Type.DIRECT);

    /**
     * @deprecated
     */
    @Deprecated
    public void setProxyType(Property<Proxy.Type> proxyType) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .type(proxyType)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The address of the proxy server.")
    @Deprecated
    private final Property<String> proxyAddress;

    /**
     * @deprecated
     */
    @Deprecated
    public void setProxyAddress(Property<String> proxyAddress) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .address(proxyAddress)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The port of the proxy server.")
    @Deprecated
    private final Property<Integer> proxyPort;

    /**
     * @deprecated
     */
    @Deprecated
    public void setProxyPort(Property<Integer> proxyPort) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .port(proxyPort)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The username for proxy authentication.")
    @Deprecated
    private final Property<String> proxyUsername;

    /**
     * @deprecated
     */
    @Deprecated
    public void setProxyUsername(Property<String> proxyUsername) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .username(proxyUsername)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The password for proxy authentication.")
    @Deprecated
    private final Property<String> proxyPassword;

    /**
     * @deprecated
     */
    @Deprecated
    public void setProxyPassword(Property<String> proxyPassword) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .password(proxyPassword)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The username for HTTP basic authentication.")
    @Deprecated
    private final Property<String> basicAuthUser;

    /**
     * @deprecated
     */
    @Deprecated
    public void setBasicAuthUser(Property<String> basicAuthUser) {
        if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
            this.auth = BasicAuthConfiguration.builder()
                .build();
        }

        this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
            .username(basicAuthUser)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The password for HTTP basic authentication.")
    @Deprecated
    private final Property<String> basicAuthPassword;

    /**
     * @deprecated
     */
    @Deprecated
    private void setBasicAuthPassword(Property<String> basicAuthPassword) {
        if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
            this.auth = BasicAuthConfiguration.builder()
                .build();
        }

        this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
            .password(basicAuthPassword)
            .build();
    }

    /**
     * @deprecated
     */
    @Schema(title = "The log level for the HTTP client.")
    @PluginProperty
    @Deprecated
    private final LogLevel logLevel;

    /**
     * @deprecated
     */
    @Deprecated
    private void setLogLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.TRACE) {
            this.logs = new LoggingType[]{
                LoggingType.REQUEST_HEADERS,
                LoggingType.REQUEST_BODY,
                LoggingType.RESPONSE_HEADERS,
                LoggingType.RESPONSE_BODY
            };
        } else if (logLevel == LogLevel.DEBUG) {
            this.logs = new LoggingType[]{
                LoggingType.REQUEST_HEADERS,
                LoggingType.RESPONSE_HEADERS,
            };
        } else if (logLevel == LogLevel.INFO) {
            this.logs = new LoggingType[]{
                LoggingType.RESPONSE_HEADERS,
            };
        }
    }

    // Deprecated properties with no real value to be kept, silently ignore
    /**
     * @deprecated
     */
    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Builder.Default
    @Deprecated
    private final Property<Duration> readIdleTimeout = Property.of(Duration.of(HttpClientConfiguration.DEFAULT_READ_IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES));

    /**
     * @deprecated
     */
    @Schema(title = "The time an idle connection can remain in the client's connection pool before being closed.")
    @Builder.Default
    @Deprecated
    private final Property<Duration> connectionPoolIdleTimeout = Property.of(Duration.ofSeconds(HttpClientConfiguration.DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS));

    /**
     * @deprecated
     */
    @Schema(title = "The maximum content length of the response.")
    @Builder.Default
    @Deprecated
    private final Property<Integer> maxContentLength = Property.of(HttpClientConfiguration.DEFAULT_MAX_CONTENT_LENGTH);
}
