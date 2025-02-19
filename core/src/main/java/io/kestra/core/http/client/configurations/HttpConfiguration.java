package io.kestra.core.http.client.configurations;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.logging.LogLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Builder(toBuilder = true)
@Getter
@Jacksonized
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
    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    @Deprecated
    private final Duration connectTimeout;

    @Schema(title = "The maximum time allowed for reading data from the server before failing.")
    @Deprecated
    private final Duration readTimeout;

    @Schema(title = "The type of proxy to use.")
    @Deprecated
    private final Proxy.Type proxyType;

    @Schema(title = "The address of the proxy server.")
    @Deprecated
    private final String proxyAddress;

    @Schema(title = "The port of the proxy server.")
    @Deprecated
    private final Integer proxyPort;

    @Schema(title = "The username for proxy authentication.")
    @Deprecated
    private final String proxyUsername;

    @Schema(title = "The password for proxy authentication.")
    @Deprecated
    private final String proxyPassword;

    @Schema(title = "The username for HTTP basic authentication.")
    @Deprecated
    private final String basicAuthUser;

    @Schema(title = "The password for HTTP basic authentication.")
    @Deprecated
    private final String basicAuthPassword;

    @Schema(title = "The log level for the HTTP client.")
    @PluginProperty
    @Deprecated
    private final LogLevel logLevel;

    // Deprecated properties with no equivalent value to be kept, silently ignore
    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Deprecated
    private final Duration readIdleTimeout;

    @Schema(title = "The time an idle connection can remain in the client's connection pool before being closed.")
    @Deprecated
    private final Duration connectionPoolIdleTimeout;

    @Schema(title = "The maximum content length of the response.")
    @Deprecated
    private final Integer maxContentLength;

    public static class HttpConfigurationBuilder {
        @Deprecated
        public HttpConfigurationBuilder connectTimeout(Duration connectTimeout) {
            if (this.timeout == null) {
                this.timeout = TimeoutConfiguration.builder()
                    .build();
            }

            this.timeout = this.timeout.toBuilder()
                .connectTimeout(Property.of(connectTimeout))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder readTimeout(Duration readTimeout) {
            if (this.timeout == null) {
                this.timeout = TimeoutConfiguration.builder()
                    .build();
            }

            this.timeout = this.timeout.toBuilder()
                .readIdleTimeout(Property.of(readTimeout))
                .build();

            return this;
        }


        @Deprecated
        public HttpConfigurationBuilder proxyType(Proxy.Type proxyType) {
            if (this.proxy == null) {
                this.proxy = ProxyConfiguration.builder()
                    .build();
            }

            this.proxy = this.proxy.toBuilder()
                .type(Property.of(proxyType))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder proxyAddress(String proxyAddress) {
            if (this.proxy == null) {
                this.proxy = ProxyConfiguration.builder()
                    .build();
            }

            this.proxy = this.proxy.toBuilder()
                .address(Property.of(proxyAddress))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder proxyPort(Integer proxyPort) {
            if (this.proxy == null) {
                this.proxy = ProxyConfiguration.builder()
                    .build();
            }

            this.proxy = this.proxy.toBuilder()
                .port(Property.of(proxyPort))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder proxyUsername(String proxyUsername) {
            if (this.proxy == null) {
                this.proxy = ProxyConfiguration.builder()
                    .build();
            }

            this.proxy = this.proxy.toBuilder()
                .username(Property.of(proxyUsername))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder proxyPassword(String proxyPassword) {
            if (this.proxy == null) {
                this.proxy = ProxyConfiguration.builder()
                    .build();
            }

            this.proxy = this.proxy.toBuilder()
                .password(Property.of(proxyPassword))
                .build();

            return this;
        }


        @SuppressWarnings("DeprecatedIsStillUsed")
        @Deprecated
        public HttpConfigurationBuilder basicAuthUser(String basicAuthUser) {
            if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
                this.auth = BasicAuthConfiguration.builder()
                    .build();
            }

            this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
                .username(Property.of(basicAuthUser))
                .build();

            return this;
        }

        @SuppressWarnings("DeprecatedIsStillUsed")
        @Deprecated
        public HttpConfigurationBuilder basicAuthPassword(String basicAuthPassword) {
            if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
                this.auth = BasicAuthConfiguration.builder()
                    .build();
            }

            this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
                .password(Property.of(basicAuthPassword))
                .build();

            return this;
        }

        @Deprecated
        public HttpConfigurationBuilder logLevel(LogLevel logLevel) {
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

            return this;
        }
    }
}
