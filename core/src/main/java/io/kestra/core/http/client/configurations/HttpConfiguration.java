package io.kestra.core.http.client.configurations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;

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

    @Schema(title = "The default charset for the request.")
    @Builder.Default
    private final Property<Charset> defaultCharset = Property.ofValue(StandardCharsets.UTF_8);

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
