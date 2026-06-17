package io.kestra.webserver.filter;

import java.util.Collection;
import java.util.Optional;

import org.reactivestreams.Publisher;

import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

//We want to authenticate only Kestra endpoints
@Filter("/api/v1/**")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't add this filter in EE
public class AuthenticationFilter implements HttpServerFilter {
    private static final Integer ORDER = ServerFilterPhase.SECURITY.order();
    public static final String BASIC_AUTH_COOKIE_NAME = BasicAuthService.BASIC_AUTH_COOKIE_NAME;

    @Inject
    private BasicAuthService basicAuthService;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Mono.fromCallable(() -> basicAuthService.configuration())
            .subscribeOn(Schedulers.boundedElastic())
            .flux()
            .flatMap(basicAuthConfiguration ->
            {
                String normalizedPath = normalizePath(request.getPath());
                boolean isConfigEndpoint = "/api/v1/configs".equals(normalizedPath)
                    || ((normalizedPath.matches("/api/v1(/[^/]+)?/basicAuth") || "/api/v1/basicAuthValidationErrors".equals(normalizedPath))
                        && !basicAuthService.isBasicAuthInitialized());

                boolean isOpenUrl = Optional.ofNullable(basicAuthConfiguration.openUrls())
                    .map(Collection::stream)
                    .map(stream -> stream.anyMatch(s -> request.getPath().startsWith(s)))
                    .orElse(false);

                if (isConfigEndpoint || isOpenUrl || isManagementEndpoint(request)) {
                    return chain.proceed(request);
                }

                if (!basicAuthService.isAuthenticated(request)) {
                    Boolean isFromLoginPage = Optional.ofNullable(request.getHeaders().get("Referer"))
                        .map(referer -> referer.split("\\?")[0].endsWith("/login"))
                        .orElse(false);

                    return Mono.just(HttpResponse.unauthorized())
                        .map(response -> isFromLoginPage ? response : response.header("WWW-Authenticate", "Basic"));
                }

                return chain.proceed(request);
            });
    }

    private static String normalizePath(String path) {
        return path.replaceAll("/+", "/");
    }

    @SuppressWarnings("rawtypes")
    private boolean isManagementEndpoint(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            return method.getAnnotation(Endpoint.class) != null;
        }
        return false;
    }
}
