package io.kestra.core.http.client.configurations;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.Proxy;

@Getter
@Builder(toBuilder = true)
public class ProxyConfiguration {
    @Schema(title = "The type of proxy to use.")
    @Builder.Default
    private final Property<java.net.Proxy.Type> type = Property.of(Proxy.Type.DIRECT);

    @Schema(title = "The address of the proxy server.")
    private final Property<String> address;

    @Schema(title = "The port of the proxy server.")
    private final Property<Integer> port;

    @Schema(title = "The username for proxy authentication.")
    private final Property<String> username;

    @Schema(title = "The password for proxy authentication.")
    private final Property<String> password;
}
