package io.kestra.mcp;

import java.security.Principal;
import java.util.Optional;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

/**
 * Identifies the caller by the name of the request's principal.
 */
@Singleton
public class DefaultMcpToolAccessControl implements McpToolAccessControl {
    @Override
    public Optional<String> callerId(HttpRequest<?> request) {
        return request.getUserPrincipal().map(Principal::getName);
    }
}
