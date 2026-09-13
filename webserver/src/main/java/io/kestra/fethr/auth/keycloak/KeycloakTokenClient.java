package io.kestra.fethr.auth.keycloak;

import java.util.Map;

import org.keycloak.representations.AccessTokenResponse;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/**
 * Typed client for the Keycloak realm token endpoint. Used for both the user {@code password}
 * (Direct Grant) sign-in and the {@code refresh_token} grant. The Keycloak admin SDK is not used
 * for either: it only manages its own service-account token tied to its instance, and running a
 * user password grant through it invalidated the user session when the client was closed
 * ({@code invalid_grant: Session not active}). Sign-in and refresh therefore both go through this
 * endpoint; the admin SDK is reserved for admin tasks (create user, assign role, logout by id).
 */
@Client("${kestra.server.keycloak.base-url:http://localhost}")
@Requires(property = "kestra.server.keycloak.base-url", pattern = ".+")
public interface KeycloakTokenClient {

    @Post(
        value = "/realms/${kestra.server.keycloak.realm:main}/protocol/openid-connect/token",
        produces = MediaType.APPLICATION_FORM_URLENCODED,
        consumes = MediaType.APPLICATION_JSON
    )
    AccessTokenResponse token(@Body Map<String, String> form);
}
