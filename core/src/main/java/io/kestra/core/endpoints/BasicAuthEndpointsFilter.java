package io.kestra.core.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Filter("/**")
@Requires(property = "endpoints.all.basic-auth")
public class BasicAuthEndpointsFilter extends OncePerRequestHttpServerFilter {
    private final EndpointBasicAuthConfiguration endpointBasicAuthConfiguration;

    public BasicAuthEndpointsFilter(EndpointBasicAuthConfiguration endpointBasicAuthConfiguration) {
        this.endpointBasicAuthConfiguration = endpointBasicAuthConfiguration;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch) {
            ExecutableMethod<?, ?> method = ((MethodBasedRouteMatch<?, ?>) routeMatch.get()).getExecutableMethod();
            if (method.getAnnotation(Endpoint.class) != null) {
                if (!validateUser(request)) {
                    return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
                }
            }
        }

        return chain.proceed(request);
    }

    private boolean validateUser(HttpRequest<?> request) {
        final String authorization = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC)) {
            String base64Credentials = authorization.substring(6);
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);

            final String[] values = credentials.split(":", 2);
            if (values.length == 2) {
                return this.endpointBasicAuthConfiguration.getUsername().equals(values[0]) &&
                    this.endpointBasicAuthConfiguration.getPassword().equals(values[1]);
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }
}
