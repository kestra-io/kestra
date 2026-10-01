package io.kestra.mcp;

import io.kestra.webserver.controllers.api.McpToolController;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.MethodBasedRouteMatch;

import java.util.Optional;

/**
 * Resolves path variables of the matched {@link McpToolController} route the way the controller itself
 * receives them, i.e. URL-decoded, instead of splitting {@link HttpRequest#getPath()},
 * which is the raw, still-percent-encoded request target and can
 * disagree with the controller on a request whose id or tenant is percent-encoded.
 */
public final class McpToolRoute {

    private McpToolRoute() {
    }

    /**
     * @return the {@code id} path variable of the matched {@link McpToolController} route, or empty when
     * the request does not target that controller.
     */
    public static Optional<String> serverId(HttpRequest<?> request) {
        return variable(request, "id");
    }

    /**
     * @return the {@code tenant} path variable of the matched {@link McpToolController} route, or empty
     * when the request does not target that controller.
     */
    public static Optional<String> tenantId(HttpRequest<?> request) {
        return variable(request, "tenant");
    }

    private static Optional<String> variable(HttpRequest<?> request, String name) {
        if (!(BasicHttpAttributes.getRouteMatchInfo(request).orElse(null) instanceof MethodBasedRouteMatch<?, ?> route)
            || !McpToolController.class.isAssignableFrom(route.getDeclaringType())) {
            return Optional.empty();
        }
        return Optional.ofNullable(route.getVariableValues().get(name))
            .filter(String.class::isInstance)
            .map(String.class::cast);
    }
}
