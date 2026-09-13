package io.kestra.fethr.auth.keycloak;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Factory
public class KeycloakAdminClientFactory {

    @Singleton
    @Context
    @Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
    @Requires(property = "micronaut.security.enabled", value = "true")
    public Keycloak keycloakAdminClient(KeycloakService.KeycloakConfiguration configuration) {
        if (!configuration.isConfigured()) {
            log.error("Keycloak admin client cannot be created: kestra.server.keycloak base-url and realm must be configured when micronaut.security.enabled is true");
            throw new IllegalStateException("Keycloak configuration is missing or invalid: set kestra.server.keycloak.base-url and kestra.server.keycloak.realm");
        }
        return KeycloakBuilder.builder()
            .serverUrl(configuration.baseUrl())
            .realm(configuration.realm())
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(configuration.clientId())
            .clientSecret(configuration.clientSecret())
            .build();
    }
}
