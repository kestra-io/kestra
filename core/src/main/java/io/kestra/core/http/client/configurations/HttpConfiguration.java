package io.kestra.core.http.client.configurations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Exponential;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

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

    @Schema(title = "The authentication to use.")
    private AbstractAuthConfiguration auth;

    @Setter
    @Schema(title = "The SSL request options")
    private SslOptions ssl;

    @Schema(title = "Whether redirects should be followed automatically.")
    @Builder.Default
    private Property<Boolean> followRedirects = Property.ofValue(true);

    @Setter
    @Schema(title = "If true, allow a failed response code (response code >= 400)")
    @Builder.Default
    private Property<Boolean> allowFailed = Property.ofValue(false);

    @Setter
    @Schema(title = "List of response code allowed for this request")
    private Property<List<Integer>> allowedResponseCodes;

    @Schema(
        title = "Whether to enable TCP Keep-Alive extended socket options (TCP_KEEPIDLE, TCP_KEEPINTERVAL, TCP_KEEPCOUNT).",
        description = "Set to `false` when running on Windows workers, as these extended socket options are not supported by the Windows JDK and will cause connection failures."
    )
    @Builder.Default
    private Property<Boolean> enabledTcpExtendedKeepAlive = Property.ofValue(true);

    @Schema(title = "The default charset for the request.")
    @Builder.Default
    private final Property<Charset> defaultCharset = Property.ofValue(StandardCharsets.UTF_8);

    @Schema(title = "Retry strategy for HTTP requests.", description = "retry is disabled by default.")
    @Builder.Default
    private AbstractRetry retry = Exponential.builder()
        .interval(Duration.ofMillis(1000))
        .maxInterval(Duration.ofSeconds(30))
        .maxAttempts(1)                                    
        .build();

    @Setter
    @Schema(
        title = "HTTP status codes that should be retried.",
        description = "Used for idempotent methods (GET, HEAD) without their own entry in " +
    "`retryOnStatusCodesByMethod`. Non-idempotent methods are never retried on a status code unless " +
    "listed in `retryOnStatusCodesByMethod`. Defaults to 502, 503 and 504 for idempotent methods (GET, HEAD) only."
    )
    @Builder.Default
    private Property<List<Integer>> retryOnStatusCodes = Property.ofValue(List.of(502, 503, 504));

    @Setter
    @Schema(
        title = "Per-HTTP-method overrides for which status codes should be retried.",
        description = "Keys are HTTP methods (e.g. `GET`, `POST`). " +
            "A method with no entry here falls back to `retryOnStatusCodes`. This lets a caller retry more " +
            "broadly for safe, read-only methods (for example, every 5xx and a 429 for GET/HEAD) while keeping " +
            "a narrower list for methods that write, where retrying a request that may have already been " +
            "processed by the origin risks duplicating the effect. Example: " +
            "`{GET: [429, 500, 502, 503, 504], HEAD: [429, 500, 502, 503, 504]}`."
    )
    private Property<Map<String, List<Integer>>> retryOnStatusCodesByMethod;

    @Setter
    @Schema(
        title = "HTTP methods for which any transport-level failure may be retried.",
        description = "A transport-level failure is a connection or timeout error rather than an HTTP response " +
            "(e.g. a read timeout or a connection reset mid-request). For a method listed here, any such failure " +
            "is retried. Defaults to GET and HEAD HTTP methods."
    )
    @Builder.Default
    private Property<List<String>> retryableTransportFailureMethods = Property.ofValue(List.of("GET", "HEAD"));

    @Schema(title = "The enabled log.")
    @PluginProperty
    private LoggingType[] logs;

    public enum LoggingType {
        REQUEST_HEADERS,
        REQUEST_BODY,
        RESPONSE_HEADERS,
        RESPONSE_BODY
    }
}
