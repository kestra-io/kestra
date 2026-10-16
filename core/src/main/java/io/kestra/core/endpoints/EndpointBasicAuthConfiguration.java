package io.kestra.core.endpoints;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

@Getter
@ConfigurationProperties("endpoints.all.basicAuth")
public class EndpointBasicAuthConfiguration {
    String username;
    String password;
}
