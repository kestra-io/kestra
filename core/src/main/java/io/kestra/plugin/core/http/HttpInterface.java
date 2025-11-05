package io.kestra.plugin.core.http;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface HttpInterface {
    @Schema(
            title = "The fully-qualified URI that points to the HTTP destination"
    )
    Property<String> getUri();

    @Schema(
            title = "The HTTP method to use"
    )
    Property<String> getMethod();

    @Schema(
        title = "The query string parameter to use",
        description = "Adds parameter to URI query. The parameter name and value are expected to be unescaped and may contain non ASCII characters.\n" +
            "The value can be a string or a list of strings.\n" +
            "This method will not override parameters already existing on `uri` and will add them as array."
    )
    Property<Map<String, Object>> getParams();

    @Schema(
            title = "The full body as a string"
    )
    Property<String> getBody();

    @Schema(
            title = "The form data to be send"
    )
    Property<Map<String, Object>> getFormData();

    @Schema(
            title = "The request content type"
    )
    Property<String> getContentType();

    @Schema(
            title = "The headers to pass to the request"
    )
    Property<Map<CharSequence, CharSequence>> getHeaders();

    @Schema(
            title = "The HTTP request options"
    )
    HttpConfiguration getOptions();

    @Schema(
        title = "The SSL request options",
        description = "This property is deprecated. Instead use the `options.ssl` property."
    )
    SslOptions getSslOptions();
}
